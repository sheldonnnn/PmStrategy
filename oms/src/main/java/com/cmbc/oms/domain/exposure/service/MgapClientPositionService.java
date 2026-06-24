package com.cmbc.oms.domain.exposure.service;

import java.math.RoundingMode;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService;
import com.cmbc.mds.forex.quotes.dto.LatchedQuoteWrapper;
import com.cmbc.mds.forex.quotes.dto.PlayPrices;
import com.cmbc.oms.controller.dto.RCode;
import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.domain.exposure.dto.MgapPosResponse;
import com.cmbc.oms.domain.exposure.dto.PositionDataResponse;
import com.cmbc.oms.domain.exposure.model.*;
import com.cmbc.oms.domain.exposure.entity.MgapPositionBalanceEntity;
import com.cmbc.oms.domain.exposure.vo.PositionVo;
import com.cmbc.oms.domain.exposure.vo.SymbolPositionVo;
import com.cmbc.oms.domain.facade.strategy.api.MgapPositionUpdateListener;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import com.cmbc.oms.infrastructure.dao.MgapPositionBalanceMapper;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import com.cmbc.oms.infrastructure.util.DateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@EnableScheduling
public class MgapClientPositionService {
    private final static Logger logger = LoggerFactory.getLogger(MgapClientPositionService.class);
    private Map<String, MgapPositionSnapshot> mgapPositionCache = new ConcurrentHashMap<>(); // 保存当天的客盘头寸汇总数据
    @Value("${mgap.url}")
    String url;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private QuantPositionManager quantPositionManager;
    @Autowired
    private MergeQuotesLatchedCacheService mergeQuotesLatchedCacheService;
    @Autowired
    private MgapPositionBalanceMapper mgapPositionBalanceMapper;
    @Autowired
    private BasicParamCacheManager basicParamCacheManager;
    // 监听者列表
    private final List<MgapPositionUpdateListener> listeners = new CopyOnWriteArrayList<>();
    // 熔断相关参数
    private static final Integer MAX_FAIL_COUNT = 4;
    private static final Integer REPLY_INTERVAL = 30000;
    // 熔断状态
    private volatile boolean isCircuitOpen = false;
    private volatile boolean isConnected = false;
    private volatile long lastFailTime = 0;
    private AtomicInteger failCount = new AtomicInteger(0);
    // private Map<String, GoldPrice> priceCache = new HashMap<>();//行情数据缓存
    @Value("${mgap.mock.url}")
    private String hedgeurl;
    @Value("${mgap.mock.islock}")
    private Boolean isPositionMock; // 是否调用头寸的假数据接口，true：调用 false：不调用
    @Autowired
    @Qualifier("mgapPollingTaskScheduler")
    private ThreadPoolTaskScheduler pollingTaskScheduler;
    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        pollingTaskScheduler.scheduleWithFixedDelay(this::syncMgapPosition, 1000);
    }

    public MgapPosResponse syncMgapPositionMock() {
        MgapPosResponse res = new MgapPosResponse();
        RCode code = new RCode();
        code.setType("0");
        code.setCode("AAAAA");
        res.setReturnCode(code);
        try {
            Map<String, MgapPositionSnapshot> totalAll = new HashMap<>();
            Map<String, String> forObject = restTemplate.getForObject(hedgeurl, Map.class);
            if (null != forObject) {
                for (Map.Entry<String, String> entry : forObject.entrySet()) {
                    String symbol = entry.getKey();
                    MgapPositionSnapshot mgapPositionSnapshot = objectMapper.convertValue(entry.getValue(), MgapPositionSnapshot.class);
                    totalAll.put(symbol, mgapPositionSnapshot);
                }
            }
            res.setTotalAll(totalAll);
        } catch (Exception e) {
            logger.error("mock position error :{}", e);
            return null;
        }
        return res;
    }

    // 定时从外汇系统同步客盘头寸
    public void syncMgapPosition() {
        if (isCircuitOpen) {
            if (System.currentTimeMillis() - lastFailTime < REPLY_INTERVAL) {
                logger.info("...熔断期内，不查客盘...");
                return;
            }
        }
        
        try {
            // (此处代码在截图中折叠了，直接进行状态赋值并处理监听器)
            MgapPosResponse mgapPosResponse = null;
            if (isPositionMock != null && isPositionMock) {
                mgapPosResponse = this.syncMgapPositionMock();
            } else {
                mgapPosResponse = restTemplate.postForObject(url, null, MgapPosResponse.class);
            }
            if (mgapPosResponse != null && "0".equals(mgapPosResponse.getReturnCode().getType())) {
                handleSuccess();
                this.mgapPositionCache = new ConcurrentHashMap<>(mgapPosResponse.getTotalAll());
                this.isConnected = true;
                for (MgapPositionUpdateListener listener : listeners) {
                    listener.onMgapPositionUpdate();
                }
            } else {
                this.mgapPositionCache.clear(); // 查询失败则清除头寸数据
                this.isConnected = false;
            }
        } catch (Exception e) {
            handleFail(e);
            logger.error("请求客盘客盘头寸接口异常!!", e);
        }
    }

    public void registerListener(MgapPositionUpdateListener listener) {
        this.listeners.add(listener);
    }

    /**
     * 新外汇系统查询所有的头寸汇总数据
     */
    public PositionDataResponse getPositionSummary(String fxSymbol) {
        PositionDataResponse hedgedPosition = new PositionDataResponse();
        // 开始调用接口查询头寸数据
        try {
            // 获取头寸数据
            PositionDataResponse mgapPositionInfo = aggregateClientPositions(fxSymbol);
            // 获取hedged头寸数据
            hedgedPosition = aggregateHedgePositions(fxSymbol);
            PositionSummary hedgePositionSummary = hedgedPosition.getPositionSummary(); // quant汇总数据
            PositionSummary mgapPositionSummary = mgapPositionInfo.getPositionSummary(); // 客盘头寸汇总数据
            if (null == hedgePositionSummary || null == mgapPositionSummary) {
                logger.error("敞口数据汇总失败!");
                return hedgedPosition;
            }
            // 客盘头寸执行集成
            hedgePositionSummary.setNetPositionXAU(hedgePositionSummary.getNetPositionXAU()
                    .add(mgapPositionSummary.getNetPositionXAU() != null ? mgapPositionSummary.getNetPositionXAU() : BigDecimal.ZERO));
            hedgePositionSummary.setNetPositionUSD(hedgePositionSummary.getNetPositionUSD()
                    .add(mgapPositionSummary.getNetPositionUSD() != null ? mgapPositionSummary.getNetPositionUSD() : BigDecimal.ZERO));
            hedgePositionSummary.setProfitAndLoss(hedgePositionSummary.getProfitAndLoss()
                    .add(mgapPositionSummary.getProfitAndLoss() != null ? mgapPositionSummary.getProfitAndLoss() : BigDecimal.ZERO)); // 浮动总盈亏
            
            return hedgedPosition;
        } catch (Exception e) {
            logger.error("查询头寸汇总异常", e);
            return hedgedPosition;
        }
    }

    /**
     * 缓存中获取所有外汇客盘头寸汇总数据
     */
    public HedgePositionSummary buildStrategyPositionView() {
        if (CollectionUtils.isEmpty(mgapPositionCache)) {
            logger.error("缓存中外汇客盘头寸汇总数据为空!!");
            return null;
        }
        // 客盘头寸汇总计算
        BigDecimal mgapHedgedPosition = BigDecimal.ZERO;
        // 拆分客盘头寸
        BigDecimal clientPosition = BigDecimal.ZERO;
        // 新外汇客盘头寸更新时间
        String updateTime = null;
        HedgePositionSummary hedgePositionSummary = new HedgePositionSummary();
        for (Map.Entry<String, MgapPositionSnapshot> entry : mgapPositionCache.entrySet()) {
            String symbol = entry.getKey();
            MgapPositionSnapshot value = entry.getValue();
            if ("XAUUSD".equals(symbol)) {
                clientPosition = clientPosition.add(value.getQty());
                hedgePositionSummary.setMgapClientPrice(value.getMktPrice());
                updateTime = value.getPositionTime();
            } else if ("XAURMB".equals(symbol)) {
                mgapHedgedPosition = mgapHedgedPosition.add(value.getQty());
            } else if ("PGRMB".equals(symbol)) {
                clientPosition = clientPosition.add(value.getQty());
            } else if ("XAUUSD".equals(symbol)) {
                mgapHedgedPosition = mgapHedgedPosition.add(value.getQty().multiply(BusinessConstant.OUNCE_GRAM));
            }
        }
        if (null == updateTime || !isValid(updateTime)) {
            // MgapPosResponse mgapPosResponse = restTemplate.postForObject(url, null, MgapPosResponse.class);
            // 调用接口查询外汇客盘头寸汇总信息
            return null;
        }
        hedgePositionSummary.setMgapClientPosition(clientPosition);
        hedgePositionSummary.setMgapHedgedPosition(mgapHedgedPosition);
        hedgePositionSummary.setUpdateTime(updateTime);
        // 汇总客盘头寸大小
        return hedgePositionSummary;
    }

    /**
     * 校验头寸更新时间是否超过5s
     * @param updateTime
     * @return
     */
    public static boolean isValid(String updateTime) {
        if (StringUtils.isEmpty(updateTime)) {
            return false;
        }
        long timeMill = DateUtil.getTimeMill(updateTime);
        long now = System.currentTimeMillis();
        if (now - timeMill < 5000) {
            return true;
        }
        logger.warn("头寸数据失效 ! 最新更新时间:{}", updateTime);
        return false;
    }

    /**
     * 接口查询外汇客盘头寸汇总信息数据
     */
    public PositionDataResponse aggregateClientPositions(String fxSymbol) {
        PositionDataResponse mgapPositionResponse = new PositionDataResponse();
        List<PositionVo> mgapPositionList = new ArrayList<>();
        PositionSummary positionSummary = new PositionSummary();
        BigDecimal netPositionSummary = BigDecimal.ZERO; // 净头寸
        BigDecimal netAmountSummary = BigDecimal.ZERO; // 净敞口金额
        BigDecimal profitLossSummary = BigDecimal.ZERO; // 浮动总盈亏
        BigDecimal netPositionUSD = BigDecimal.ZERO; // 美元敞口
        BigDecimal netPositionXAU = BigDecimal.ZERO; // XAU头寸
        int id = 1;
        if (CollectionUtils.isEmpty(this.mgapPositionCache.entrySet())) {
            return mgapPositionResponse;
        }
        for (Map.Entry<String, MgapPositionSnapshot> entry : this.mgapPositionCache.entrySet()) {
            String symbol = entry.getKey();
            MgapPositionSnapshot value = entry.getValue();
            // 组装返回的头寸列表
            PositionVo mgapPosition = new PositionVo();
            mgapPosition.setId(id++); // 序号
            mgapPosition.setSymbol(value.getSymbol()); // 币种
            mgapPosition.setSymbol(value.getCurrency()); // 交易品种
            mgapPosition.setNetPosition(value.getQty()); // 净头寸重量
            mgapPosition.setNetAmount(value.getAmount()); // 净敞口金额
            mgapPosition.setUpdateTime(value.getPositionTime()); // 头寸时间
            mgapPosition.setDepthUpdateTime(
                    value.getPositionTime() == null ? null : value.getPositionTime().replaceAll("[^0-9]", "")); // 行情更新时间
            mgapPosition.setProfitLoss(value.getUnrealizedPL()); // 浮动盈亏
            mgapPosition.setPrice(value.getMktPrice()); // 最新价
            
            if ("XAUUSD".equals(symbol) || "XAGUSD".equals(symbol) || "PDUSD".equals(symbol)) {
                netPositionSummary = netPositionSummary.add(value.getQty()); // 净头寸汇总
                netAmountSummary = netAmountSummary.add(value.getAmount()); // 净敞口汇总金额
                positionSummary.setUpdateTime(value.getPositionTime()); // 头寸时间
            } else if ("XAURMB".equals(symbol)) {
                netPositionUSD = netPositionUSD.add(value.getAmount()); // 美元敞口总额
                netPositionXAU = netPositionXAU.add(value.getQty()); // XAU头寸
                netPositionSummary = netPositionSummary.add(value.getQty().multiply(BusinessConstant.OUNCE_GRAM).setScale(4, BigDecimal.ROUND_HALF_UP)); // 净头寸计算
            }
            
            BigDecimal usdPnL = BigDecimal.ZERO;
            if (value.getQty().compareTo(BigDecimal.ZERO) != 0) {
                usdPnL = value.getAmount(); // 浮动盈亏
            }
            
            PlayPrices xauPrice = mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol(symbol);
            if (xauPrice == null) {
                xauPrice = mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol("XAUUSD");
            }
            
            if (value.getQty().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal avgPrice = value.getAmount().divide(value.getQty(), 5, BigDecimal.ROUND_HALF_UP).negate(); // 假设金额负数代表支出XAU头寸
                mgapPosition.setAvgPrice(avgPrice);
                if (xauPrice != null && xauPrice.getMktPx() != null) {
                    BigDecimal xauMktPrice = xauPrice.getMktPx();
                    mgapPosition.setMktPrice(xauMktPrice); // XAUUSD最新价
                    usdPnL = xauMktPrice.multiply(value.getQty()).add(value.getAmount());
                    mgapPosition.setDepthUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
                }
            }
            
            PlayPrices fxRatePrice = mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol(fxSymbol);
            if (fxRatePrice != null && fxRatePrice.getMktPx() != null) {
                BigDecimal fxRate = fxRatePrice.getMktPx();
                // ...
                BigDecimal unrealizedPL = usdPnL.multiply(fxRate).setScale(2, BigDecimal.ROUND_HALF_UP);
                mgapPosition.setProfitLoss(unrealizedPL);
                profitLossSummary = profitLossSummary.add(unrealizedPL); // 浮动总盈亏
            }
            
            mgapPositionList.add(mgapPosition);
        }
        
        // 敞口汇总设置
        positionSummary.setNetPositionUSD(netPositionUSD); // 美元敞口
        positionSummary.setNetPositionXAU(netPositionXAU); // XAU头寸
        positionSummary.setProfitAndLoss(profitLossSummary); // 浮动总盈亏
        positionSummary.setSymbol("XAURMB"); // 交易品种
        positionSummary.setNetPosition(netPositionSummary); // 净敞口头寸
        positionSummary.setNetAmount(netAmountSummary); // 净敞口金额
        if (BigDecimal.ZERO.compareTo(positionSummary.getNetPosition()) != 0) {
            positionSummary.setPrice(netAmountSummary.divide(netPositionSummary, 5, BigDecimal.ROUND_HALF_UP));
        }
        
        mgapPositionResponse.setMgapPosition(mgapPositionList);
        mgapPositionResponse.setPositionSummary(positionSummary);
        logger.info("客盘汇总数据组装完成:{}", mgapPositionResponse);
        return mgapPositionResponse;
    }

    /**
     * 获取平台查询出的持仓
     * 外汇对冲头寸查询持仓
     */
    public PositionDataResponse aggregateHedgePositions(String fxSymbol) {
        PositionDataResponse hedgedPositionResponse = new PositionDataResponse();
        List<PositionVo> hedgedPositionList = new ArrayList<>();
        PositionSummary positionSummary = new PositionSummary();
        BigDecimal netPositionSummary = BigDecimal.ZERO; // 净敞口头寸
        BigDecimal netAmountSummary = BigDecimal.ZERO; // 净敞口金额
        BigDecimal profitLossSummary = BigDecimal.ZERO; // 浮动总盈亏
        BigDecimal netPositionUSD = BigDecimal.ZERO; // 美元敞口
        BigDecimal netPositionXAU = BigDecimal.ZERO; // XAU头寸
        // 开始调用接口查询头寸数据
        List<SymbolPositionsVo> symbolPositions = getHedgePositionDetail();
        if (null != symbolPositions) {
            int id = 1;
            for (SymbolPositionsVo positionSnapshot : symbolPositions) {
                PositionVo info = new PositionVo();
                info.setId(id++);
                info.setSymbol(positionSnapshot.getSymbol());
                ContractInfoBasic contractInfoBasic = basicParamCacheManager.getContractInfo(positionSnapshot.getSymbol());
                info.setMktPrice(positionSnapshot.getMktPrice());
                info.setAvgPrice(positionSnapshot.getAvgPrice()); // 成本价/均价
                info.setProfitLoss(positionSnapshot.getFloatPnL()); // 浮动盈亏
                // 头寸更新时间
                info.setUpdateTime(positionSnapshot.getUpdateTime());
                info.setDepthUpdateTime(positionSnapshot.getDepthUpdateTime() != null ? positionSnapshot.getDepthUpdateTime().replaceAll("[^0-9]", "") : null);
                
                if (BaseConstants.DOMESTIC_TYPE_INNER.equals(positionSnapshot.getDomesticType())) {
                    info.setNetPosition(positionSnapshot.getNetWeight()); // 净头寸
                    netAmountSummary = netAmountSummary.add(positionSnapshot.getAmount()); // 净敞口金额
                    netPositionSummary = netPositionSummary.add(positionSnapshot.getNetWeight()); // 净敞口头寸
                    profitLossSummary = profitLossSummary.add(positionSnapshot.getFloatPnL()); // 浮动总盈亏
                    
                    if (contractInfoBasic != null && BusinessConstant.SM_FUTURES_EXCHANGE.equals(contractInfoBasic.getExchangeCode())) {
                        PlayPrices playPrice = mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol(positionSnapshot.getSymbol());
                        if (playPrice != null && playPrice.getMktPx() != null) {
                            BigDecimal baseSpread = playPrice.getMktPx().subtract(positionSnapshot.getAvgPrice()).setScale(2, BigDecimal.ROUND_HALF_UP);
                            info.setBaseSpread(baseSpread);
                        }
                    }
                } else {
                    info.setNetPosition(positionSnapshot.getQty()); // 净头寸
                    netPositionUSD = netPositionUSD.add(positionSnapshot.getAmount()); // 美元敞口金额
                    netPositionXAU = netPositionXAU.add(positionSnapshot.getQty()); // XAU头寸
                    
                    netPositionSummary = netPositionSummary.add(info.getNetPosition().multiply(BusinessConstant.OUNCE_GRAM)); // 净敞口头寸
                    
                    PlayPrices basePlayPrice = mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol("XAUUSD");
                    if (basePlayPrice != null && basePlayPrice.getMktPx() != null) {
                        // 价格计算 美元/盎司 转换为 元/克
                        BigDecimal mktPrice = positionSnapshot.getMktPrice().multiply(basePlayPrice.getMktPx()).divide(BusinessConstant.OUNCE_GRAM, 2, BigDecimal.ROUND_HALF_UP);
                        info.setMktPrice(mktPrice);
                        info.setBaseSpread(mktPrice.subtract(positionSnapshot.getAvgPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
                        info.setProfitLoss(info.getBaseSpread().multiply(info.getNetPosition()).setScale(2, BigDecimal.ROUND_HALF_UP));
                    }
                }
                hedgedPositionList.add(info);
            }
        }
        
        // 组装返回的数据模型
        positionSummary.setNetPositionXAU(netPositionXAU);
        positionSummary.setNetPositionUSD(netPositionUSD);
        positionSummary.setProfitAndLoss(profitLossSummary); // 浮动总盈亏
        positionSummary.setNetPosition(netPositionSummary); // 净敞口头寸
        positionSummary.setNetAmount(netAmountSummary); // 净敞口金额
        
        positionSummary.setSymbol("XAURMB"); // 交易品种
        if (BigDecimal.ZERO.compareTo(positionSummary.getNetPosition()) != 0) {
            positionSummary.setPrice(positionSummary.getNetAmount().divide(positionSummary.getNetPosition(), 5, BigDecimal.ROUND_HALF_UP));
        }
        
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        positionSummary.setUpdateTime(formatter.format(new Date())); // 拿到时间
        hedgedPositionResponse.setMgapPosition(hedgedPositionList); // 变化子盘头寸数据
        hedgedPositionResponse.setPositionSummary(positionSummary);
        return hedgedPositionResponse;
    }

    /**
     * 组装给前端返回量化持仓汇总（盘前+盘中）
     * @return
     */
    public List<SymbolPositionVo> getHedgePositionSummaryByMarket() {
        BigDecimal netAmountOutSummary = BigDecimal.ZERO; // 场外开盘金额汇总
        BigDecimal netAmountInSummary = BigDecimal.ZERO; // 场内开盘金额汇总
        BigDecimal netPositionOutSummary = BigDecimal.ZERO; // 净头寸 场外（盎司）——数量 汇总-场外
        BigDecimal netPositionInSummary = BigDecimal.ZERO; // 净头寸 场内（盎司）——数量 汇总-场内
        BigDecimal profitLossOutSummary = BigDecimal.ZERO; // 浮动盈亏 场外
        BigDecimal profitLossInSummary = BigDecimal.ZERO; // 浮动盈亏 场内
        List<SymbolPositionVo> symbolPositionVoList = new ArrayList<>();
        try {
            // 开始统计量化盘中头寸数据
            String xauUpdateTime = null;
            List<SymbolPositionsVo> symbolPositions = getHedgePositionDetail();
            if (null != symbolPositions) {
                for (SymbolPositionsVo positionSnapshot : symbolPositions) {
                    if (positionSnapshot.getSymbol() != null) {
                        xauUpdateTime = positionSnapshot.getUpdateTime();
                        if (BaseConstants.DOMESTIC_TYPE_INNER.equals(positionSnapshot.getDomesticType())) {
                            netAmountInSummary = netAmountInSummary.add(positionSnapshot.getAmount()); // 场内开盘金额汇总
                            netPositionInSummary = netPositionInSummary.add(positionSnapshot.getNetWeight()); // 净头寸 场内（盎司）
                            profitLossInSummary = profitLossInSummary.add(positionSnapshot.getFloatPnL()); // 浮动盈亏 场内
                        } else {
                            netAmountOutSummary = netAmountOutSummary.add(positionSnapshot.getAmount()); // 场外开盘金额汇总
                            netPositionOutSummary = netPositionOutSummary.add(positionSnapshot.getQty()); // 净头寸 场外（盎司）
                            profitLossOutSummary = profitLossOutSummary.add(positionSnapshot.getFloatPnL()); // 浮动盈亏 场外
                        }
                    }
                }
            }
            
            Date now = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            
            SymbolPositionVo positionOut = new SymbolPositionVo(); // 场外按量汇总
            positionOut.setSymbol("XAUUSD");
            positionOut.setNetPosition(netPositionOutSummary); // 净头寸 场外（盎司）
            positionOut.setNetAmount(netAmountOutSummary); // 场外开盘金额汇总
            positionOut.setProfitLoss(profitLossOutSummary); // 浮动盈亏 场外
            positionOut.setDomesticType(BaseConstants.DOMESTIC_TYPE_OUTER); // 场外
            if (positionOut.getNetPosition().compareTo(BigDecimal.ZERO) != 0) {
                positionOut.setAvgPrice(netAmountOutSummary.divide(positionOut.getNetPosition(), 2, RoundingMode.HALF_UP));
            }
            positionOut.setUpdateTime(format.format(now));
            positionOut.setDepthUpdateTime(formatter.format(now));
            symbolPositionVoList.add(positionOut);
            
            SymbolPositionVo positionIn = new SymbolPositionVo(); // 场内按量汇总
            positionIn.setSymbol("XAURMB");
            positionIn.setNetPosition(netPositionInSummary.multiply(BusinessConstant.OUNCE_GRAM).setScale(2, BigDecimal.ROUND_HALF_UP)); // 净头寸 场内（克）
            positionIn.setNetAmount(netAmountInSummary); // 场内开盘金额汇总
            positionIn.setProfitLoss(profitLossInSummary); // 浮动盈亏 场内
            positionIn.setDomesticType(BaseConstants.DOMESTIC_TYPE_INNER); // 场内
            if (positionIn.getNetPosition().compareTo(BigDecimal.ZERO) != 0) {
                positionIn.setAvgPrice(netAmountInSummary.divide(positionIn.getNetPosition(), 2, RoundingMode.HALF_UP));
            }
            positionIn.setUpdateTime(format.format(now));
            positionIn.setDepthUpdateTime(formatter.format(now));
            symbolPositionVoList.add(positionIn);
            
        } catch (Exception e) {
            logger.error("量化平盘头寸数据获取异常：", e);
        }
        return symbolPositionVoList;
    }

    /**
     * 统计量化 平盘头寸数据
     */
    private List<SymbolPositionsVo> getHedgePositionDetail() {
        // 获取量化持仓所有
        List<SymbolPositionsVo> folderPosition = quantPositionManager.getFolderPosition("MgapHedge");
        if (null == folderPosition) {
            return null;
        }
        // 开始计算
        return folderPosition;
    }

    private void handleSuccess() {
        if (isCircuitOpen) {
            isCircuitOpen = false;
        }
        failCount.set(0);
    }

    private void handleFail(Exception e) {
        lastFailTime = System.currentTimeMillis();
        int count = failCount.incrementAndGet();
        if (count >= MAX_FAIL_COUNT) {
            isCircuitOpen = true;
            logger.error("请求失败过多，对冲服务不可用，将在{}秒后重试", REPLY_INTERVAL / 1000, e);
        }
    }

    /**
     * 新外汇体系下定时任务，每天 18:30:00 触发
     */
    @Scheduled(cron = "0 30 18 * * ?")
    public void persistDailyMgapPositionSnapshot() {
        if (CollectionUtils.isEmpty(this.mgapPositionCache)) {
            logger.error("客盘总计头寸缓存为空，跳过日终结转!");
            return;
        }
        
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 每日将客盘汇总、落库留痕（包含合约、美元线等）
        for (Map.Entry<String, MgapPositionSnapshot> entry : this.mgapPositionCache.entrySet()) {
            MgapPositionSnapshot positionVo = entry.getValue();
            if (positionVo != null) {
                try {
                    MgapPositionBalanceEntity entity = new MgapPositionBalanceEntity();
                    entity.setDate(today);
                    entity.setSymbol(positionVo.getSymbol());
                    entity.setAmount(positionVo.getAmount()); // 净金额
                    entity.setQty(positionVo.getQty()); // 净头寸
                    entity.setPrice(positionVo.getMktPrice()); // 最新价
                    
                    entity.setUpdateTime(positionVo.getPositionTime()); // 更新时间
                    entity.setCreateTime(LocalDateTime.now());
                    
                    mgapPositionBalanceMapper.insert(entity);
                    logger.info("客盘总计头寸结转成功: {}", entity.getSymbol());
                } catch (Exception e) {
                    logger.error("客盘总计头寸结转失败: {}", positionVo.getSymbol(), e);
                }
            }
        }
        logger.info("客盘总计头寸每日结转任务完成!");
    }
}
