package com.cmbc.oms.domain.exposure.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.mds.distribution.PloyPrices;
import com.cmbc.mds.service.MergeQuotesCacheService;
import com.cmbc.oms.constant.BaseConstants;
import com.cmbc.oms.domain.basic.BasicParamCacheManager;
import com.cmbc.oms.domain.exposure.model.FolderPosition;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.oms.domain.exposure.dto.MgapPosResponse;
import com.cmbc.oms.domain.exposure.dto.MgapPositionSnapshot;
import com.cmbc.oms.domain.exposure.dto.PositionDataResponse;
import com.cmbc.oms.domain.exposure.dto.StrategyPosition;
import com.cmbc.oms.domain.exposure.model.PositionSummary;

import com.cmbc.oms.domain.exposure.vo.PositionVo;
import com.cmbc.oms.domain.exposure.vo.SymbolPositionVo;
import com.cmbc.oms.domain.order.model.ContractInfoBasic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@EnableScheduling
public class MgapClientPositionManage {
    private final static Logger logger = LoggerFactory.getLogger(MgapClientPositionManage.class);
    private Map<String, MgapPositionSnapshot> mgapPositionCache = new ConcurrentHashMap<>();
    @Value("${mgap.url}")
    String url;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ExposureManage exposureManage;
    @Autowired
    private MergeQuotesCacheService mergeQuotesCacheService;

    @Autowired
    private BasicParamCacheManager basicParamCacheManager;

    // 熔断控制参数
    private static final Integer MAX_FAIL_COUNT = 4;
    private static final Integer REPLY_INTERVAL = 30000;

    // 状态变量
    private boolean isCircuitOpen = false;
    private boolean isConnected = false;
    private long lastFailTime = 0;
    private AtomicInteger failCount = new AtomicInteger(0);
    // private Map<String, GoldPrice> priceCache = new HashMap<>(); // 行情数据缓存

    // 定时从积存金系统查询客盘头寸
    @Scheduled(fixedDelay = 1000)
    public void syncMgapPosition() {
        try {
            if (isCircuitOpen) {
                if (System.currentTimeMillis() - lastFailTime < REPLY_INTERVAL) {
                    logger.info("--- 熔断期间，不做处理 ---");
                    return;
                }
            }
            MgapPosResponse mgapPosResponse = restTemplate.postForObject(url, null, MgapPosResponse.class);
            logger.info("积存金头寸查询结果返回结果:code={}, {}", mgapPosResponse.getReturnCode().getType(), JSONObject.toJSONString(mgapPosResponse.getTotalAll()))
            if ("S".equals(mgapPosResponse.getReturnCode())) {
                if(null == mgapPosResponse.getTotalAll()){
                    logger.error("查询积存金头寸信息，接口数据为空！！！查询结果：{}", mgapPosResponse);
                    this.mgapPositionCache.clear();
                    this.isConnected = false;
                    return;
                }
                handleSuccess();
                // 更新缓存
                mgapPositionCache = mgapPosResponse.getTotalAll();
                this.isConnected = true;
            } else {
                mgapPositionCache.clear(); // 查询失败则清除头寸数据
                this.isConnected = false;
            }
        } catch (Exception e) {
            handleFail(e);
            logger.error("查询积存金客盘头寸接口异常!!", e);
        }
    }

    /**
     * 获取所有的头寸汇总数据
     */
    public PositionDataResponse getPositionSummary(String fxSymbol) {
        PositionDataResponse hedgedPosition = new PositionDataResponse();
        try {
            // 1. 获取头寸数据
            PositionDataResponse mgapPositionInfo = getMgapPositionSummary(fxSymbol);
            // 2. 获取hedged头寸数据
            PositionDataResponse hedgedPositionResponse = getHedgedPosition(fxSymbol);
            PositionSummary hedgePositionSummary = hedgedPositionResponse.getPositionSummary(); // quant汇总数据
            PositionSummary mgapPositionSummary = mgapPositionInfo.getPositionSummary(); // 银存金头寸汇总数据
            if (null == hedgePositionSummary || null == mgapPositionSummary) {
                logger.error("头寸数据汇总失败！");
                return hedgedPosition;
            }
            // 3. 积存金头寸统计完成
            // 累加损益
            hedgePositionSummary.setProfitAndLoss(hedgePositionSummary.getProfitAndLoss()
                    .add(mgapPositionSummary.getProfitAndLoss() != null ? mgapPositionSummary.getProfitAndLoss()
                            : BigDecimal.ZERO));
            // 累加净头寸
            hedgePositionSummary.setNetPosition(hedgePositionSummary.getNetPosition()
                    .add(mgapPositionSummary.getNetPosition() != null ? mgapPositionSummary.getNetPosition()
                            : BigDecimal.ZERO));
            // 累加净金额
            hedgePositionSummary.setNetAmount(hedgePositionSummary.getNetAmount().add(
                    mgapPositionSummary.getNetAmount() != null ? mgapPositionSummary.getNetAmount() : BigDecimal.ZERO));
            if (BigDecimal.ZERO.compareTo(hedgePositionSummary.getNetPosition()) != 0) {
                // 被除数不为0，净敞口金额固定转化为负数，再做计算，均价=净敞口金额/净敞口数量，保留两位小数
                hedgePositionSummary.setAvgPrice(hedgePositionSummary.getNetAmount().negate()
                        .divide(hedgePositionSummary.getNetPosition(), 2, BigDecimal.ROUND_HALF_UP));
            }
            hedgedPosition.setMgapPosition(mgapPositionInfo.getMgapPosition());
            hedgedPosition.setConnected(this.isConnected);
            return hedgedPosition;
        } catch (Exception e) {
            logger.error("获取现存头寸汇总数据异常！！", e);
        }
        return hedgedPosition;
    }

    /**
     * 缓存中获取积存金头寸汇总数据
     */
    public StrategyPosition getMgapPositionSummaryCache() {
        StrategyPosition strategyPosition = new StrategyPosition();
        PositionDataResponse mgapPositionRes = getMgapPositionSummary(null);
        PositionSummary mgapPositionSummary = mgapPositionRes.getPositionSummary();
        if (null == mgapPositionSummary || !isValid(mgapPositionSummary.getUpdateTime())) {
            // 调用接口查询最新的汇总信息
            // MgapPosResponse mgapPosResponse = restTemplate.postForObject(url, null,
            // MgapPosResponse.class);
            // if (Objects.isNull(mgapPositionSummary) ||
            // !isValid(mgapPositionSummary.getUpdateTime())) {
            return null;
            // }
        }
        List<PositionVo> positionVos = mgapPositionRes.getMgapPosition();
        BigDecimal clientPosition = BigDecimal.ZERO;
        if (!CollectionUtils.isEmpty(positionVos)) {
            // 统计积存金客盘头寸信息及更新时间
            for (PositionVo vo : positionVos) {
                if ("PGCRMB".equals(vo.getSymbol())) {
                    clientPosition = clientPosition.add(vo.getNetPosition());
                }
                if ("KAURMB".equals(vo.getSymbol())) {
                    clientPosition = clientPosition.add(vo.getNetPosition());
                    strategyPosition.setMgapClientPositionTime(vo.getPositionUpdateTime());
                }
            }
        }
        strategyPosition.setMgapClientPosition(clientPosition);
        BigDecimal mgapPosition = mgapPositionSummary.getNetPosition();
        PositionSnapshot quantPosition = exposureManage.getTotalPosition("MgapHedge");
        BigDecimal hedgedPosition = quantPosition.getNetWeight();
        BigDecimal frozenPosition = quantPosition.getNetFrozenWeight();
        strategyPosition.setMgapNetPosition(mgapPosition);
        strategyPosition.setHedgedNetPosition(hedgedPosition);
        strategyPosition.setFrozenNetPosition(frozenPosition);
        strategyPosition.setUpdateTime(mgapPositionSummary.getUpdateTime());
        return strategyPosition;
    }

    /**
     * 校验头寸更新时间是否超过1s (此处逻辑为2000ms)
     *
     *
     *
     */
    public static boolean isValid(String updateTime) {
        if (StringUtils.isEmpty(updateTime)) {
            return true;
        }
        long timeMill = DateUtil.getTimeMill(updateTime);
        long now = System.currentTimeMillis();
        if (now - timeMill < 4000) {
            return true;
        }
        return false;
    }

    /**
     * 按接口查询积存金头寸汇总信息数据
     */
    public PositionDataResponse getMgapPositionSummary(String fxSymbol) {
        PositionDataResponse mgapPositionResponse = new PositionDataResponse();
        List<PositionVo> mgapPositionList = new ArrayList<>();
        PositionSummary positionSummary = new PositionSummary();

        BigDecimal netPositionSummary = BigDecimal.ZERO;// 净头寸敞口
        BigDecimal netDealAmountSummary = BigDecimal.ZERO;// 净敞口金额
        BigDecimal profitLossSummary = BigDecimal.ZERO;
        BigDecimal netPositionUSD = BigDecimal.ZERO;// 美元敞口
        BigDecimal netPositionXAU = BigDecimal.ZERO;// XAU敞口

        if (CollectionUtils.isEmpty(this.mgapPositionCache.entrySet())) {
            return mgapPositionResponse;
        }
        int id = 1;
        for (Map.Entry<String, MgapPositionSnapshot> entry : this.mgapPositionCache.entrySet()) {
            String key = entry.getKey();
            MgapPositionSnapshot value = entry.getValue();
            PositionVo mgapPosition = new PositionVo();
            mgapPosition.setId(id++);
            mgapPosition.setName(value.getSymbol());
            mgapPosition.setSymbol(value.getCurrency());
            mgapPosition.setNetPosition(value.getQty()); // 累计重量
            mgapPosition.setNetAmount(value.getAmt()); // 累计金额
            mgapPosition.setPositionUpdateTime(value.getPositionTime());
            String depthTime = StringUtils.isEmpty(value.getPriceTime()) ? null
                    : value.getPriceTime().replaceAll("-", "");
            mgapPosition.setDepthUpdateTime(depthTime);
            mgapPosition.setProfitLoss(value.getUnrealizedPL()); // 浮动盈亏
            mgapPosition.setAvgPrice(value.getPrice()); // 成本价
            mgapPosition.setMktPrice(value.getMktPrice()); // 市场最新价格
            // 针对特定币种进行汇总统计
            if (("KAURMB".equals(key) || "AURMB".equals(key) || "PGCRMB".equals(key))) {
                profitLossSummary = profitLossSummary.add(value.getUnrealizedPL()).setScale(2, RoundingMode.DOWN);
                netPositionSummary = netPositionSummary.add(value.getQty());
                netDealAmountSummary = netDealAmountSummary.add(value.getAmt());
                positionSummary.setUpdateTime(value.getPositionTime());
            } else if ("XAUUSD".equals(key)) {
                netPositionUSD = netPositionUSD.add(value.getAmt());
                netPositionXAU = netDealAmountSummary.add(value.getAmt());
                // 伦敦金转换逻辑
                netPositionSummary = netPositionSummary.add(value.getQty().multiply(BaseConstants.OUNCE_GRAM))
                        .setScale(4, RoundingMode.HALF_UP);
                PloyPrices fxRatePrice = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(fxSymbol);
                if (fxRatePrice != null && fxRatePrice.getMidPx() != null) {
                    BigDecimal fxRate = fxRatePrice.getMidPx();
                    netDealAmountSummary = netDealAmountSummary.add(value.getAmt().multiply(fxRate)).setScale(2,
                            RoundingMode.HALF_UP);
                    // todo XAU浮动盈亏计算...

                }
            }
            mgapPositionList.add(mgapPosition);
        }
        positionSummary.setNetPositionUSD(netPositionUSD);
        positionSummary.setNetPositionXAU(netPositionXAU);
        positionSummary.setProfitAndLoss(profitLossSummary);
        positionSummary.setSymbol("KAURMB");
        positionSummary.setNetPosition(netPositionSummary);
        positionSummary.setNetAmount(netDealAmountSummary);

        if (BigDecimal.ZERO.compareTo(netPositionSummary) != 0) {
            BigDecimal avgPrice = netDealAmountSummary.negate().divide(netPositionSummary, 2, RoundingMode.HALF_UP);
            positionSummary.setAvgPrice(avgPrice);
        }

        mgapPositionResponse.setMgapPosition(mgapPositionList);
        mgapPositionResponse.setPositionSummary(positionSummary);
        return mgapPositionResponse;
    }

    /**
     * 获取量化平盘头寸信息
     */
    public PositionDataResponse getHedgedPosition(String fxSymbol) {
        PositionDataResponse hedgedPositionResponse = new PositionDataResponse();
        List<PositionVo> hedgedPositionList = new ArrayList<>();
        PositionSummary positionSummary = new PositionSummary();

        BigDecimal netPositionSummary = BigDecimal.ZERO;
        BigDecimal netDealAmountSummary = BigDecimal.ZERO;
        BigDecimal profitLossSummary = BigDecimal.ZERO;
        BigDecimal netPositionUSD = BigDecimal.ZERO;// 美元敞口
        BigDecimal netPositionXAU = BigDecimal.ZERO;// XAU敞口

        Map<String, PositionSnapshot> symbolPositions = getGoldHedgeInfo();
        if (null != symbolPositions) {
            int id = 1;
            for (PositionSnapshot positionSnapshot : symbolPositions.values()) {
                synchronized (positionSnapshot) {
                    PositionVo info = new PositionVo();
                    info.setId(id++);
                    info.setSymbol(positionSnapshot.getSymbol());
                    ContractInfoBasic contractInfoBasic = basicParamCacheManager
                            .getContractInfo(positionSnapshot.getSymbol());
                    info.setNetAmount(positionSnapshot.getNetAmount()); // 平盘金额
                    info.setMktPrice(positionSnapshot.getMktPrice());
                    info.setAvgPrice(positionSnapshot.getNetAvgPrice()); // 成本价
                    info.setProfitLoss(positionSnapshot.getFloatPnl()); // 浮动盈亏
                    info.setPositionUpdateTime(positionSnapshot.getUpdateTime().format(DateUtil.formatterSecond));
                    if (positionSnapshot.getDepthUpdateTime() != null) {
                        info.setDepthUpdateTime(positionSnapshot.getDepthUpdateTime().format(DateUtil.formatterSecond));
                    }
                    if (BaseConstants.DOMESTIC_TYPE_INNER.equals(positionSnapshot.getDomesticType())) {
                        info.setNetPosition(positionSnapshot.getNetWeight());
                        netDealAmountSummary = netDealAmountSummary.add(info.getNetAmount());
                        netPositionSummary = netPositionSummary.add(info.getNetPosition());
                        profitLossSummary = profitLossSummary.add(info.getProfitLoss());
                        // 判断是否为上海期货交易所
                        if (contractInfoBasic.getExchCode().equals(BaseConstants.SH_FUTURES_EXCHANGE)) {
                            PloyPrices ployPrice = mergeQuotesCacheService
                                    .getSystemInitPloyPriceBySymbol(positionSnapshot.getSymbol());
                            PloyPrices basePrice = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol("Au(T+D)");
                            if (ployPrice != null && basePrice != null && ployPrice.getMidPx() != null
                                    && basePrice.getMidPx() != null) {
                                // 计算基准价差：(当前价 - Au(T+D)价)，保留2位小数，四舍五入
                                info.setBaseSpread(ployPrice.getMidPx().subtract(basePrice.getMidPx()).setScale(2,
                                        BigDecimal.ROUND_HALF_UP));
                                // 计算价差盈亏：价差 * 净头寸，保留2位小数
                                info.setSpreadPL(info.getBaseSpread().multiply(info.getNetPosition()).setScale(2,
                                        BigDecimal.ROUND_HALF_UP));
                            }
                        }
                    } else {
                        // 非内盘/期货的处理逻辑
                        info.setNetPosition(positionSnapshot.getNetQty());
                        netPositionUSD = netPositionUSD.add(positionSnapshot.getNetAmount());
                        netPositionXAU = netPositionXAU.add(positionSnapshot.getNetQty());
                        // 净头寸换算（可能涉及盎司到克的转换）
                        netPositionSummary = netPositionSummary
                                .add(info.getNetPosition().multiply(BaseConstants.OUNCE_GRAM));
                        // 获取汇率或基准价进行折算
                        if (mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(fxSymbol) != null) {
                            // TODO 1.CNH or CNY? 2.处理逻辑补充
                            BigDecimal fxRate = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(fxSymbol)
                                    .getMidPx();

                            // 净成交金额和盈亏按汇率折算
                            netDealAmountSummary = netDealAmountSummary.add(info.getNetAmount().multiply(fxRate))
                                    .setScale(2, BigDecimal.ROUND_HALF_UP);
                            profitLossSummary = profitLossSummary.add(info.getProfitLoss().multiply(fxRate)).setScale(2,
                                    BigDecimal.ROUND_HALF_UP);
                            // logger.info("positionSnapshot:{}",
                            // JSONObject.toJSONString(positionSnapshot));
                            // logger.info("outer symbol:{}, qty:{}, amt:{}, netPositionSummary:{},
                            // fxRate:{}",
                            positionSummary.setFxRate(fxRate);
                        }
                    }
                    hedgedPositionList.add(info);
                }
            }
        }

        positionSummary.setNetPositionXAU(netPositionXAU);
        positionSummary.setNetPositionUSD(netPositionUSD);
        positionSummary.setProfitAndLoss(profitLossSummary);
        positionSummary.setNetPosition(netPositionSummary);
        positionSummary.setNetAmount(netDealAmountSummary);
        positionSummary.setSymbol("KAURMB");

        if (BigDecimal.ZERO.compareTo(positionSummary.getNetPosition()) != 0) {
            BigDecimal avgPrice = positionSummary.getNetAmount().negate().divide(positionSummary.getNetPosition(), 2,
                    RoundingMode.HALF_UP);
            positionSummary.setAvgPrice(avgPrice);
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        positionSummary.setUpdateTime(formatter.format(new Date()));
        hedgedPositionResponse.setHedgedPosition(hedgedPositionList);
        hedgedPositionResponse.setPositionSummary(positionSummary);
        return hedgedPositionResponse;
    }

    /**
     * 统计量化平盘头寸数据
     * 
     * @return 境内外头寸汇总列表
     */
    public List<SymbolPositionVo> getHedgedSymbolPosition() {
        BigDecimal netAmountOutSummary = BigDecimal.ZERO; // 境外平盘金额汇总
        BigDecimal netAmountInSummary = BigDecimal.ZERO; // 境内平盘金额汇总
        BigDecimal netPositionOutSummary = BigDecimal.ZERO; // 净头寸--左头寸(盎司)---数量 汇总-境外
        BigDecimal netPositionInSummary = BigDecimal.ZERO; // 净头寸--左头寸(盎司)---数量 汇总-境内
        BigDecimal profitLossOutSummary = BigDecimal.ZERO; // 浮动盈亏-境外
        BigDecimal profitLossInSummary = BigDecimal.ZERO; // 浮动盈亏-境内

        List<SymbolPositionVo> symbolPositionVoList = new ArrayList<>();

        try {
            // 1. 获取统计量化平盘头寸数据
            Map<String, PositionSnapshot> symbolPositions = getGoldHedgeInfo();
            if (null != symbolPositions) {
                for (PositionSnapshot positionSnapshot : symbolPositions.values()) {
                    synchronized (positionSnapshot) {
                        // 逻辑：基于境内外属性累加金额与头寸
                        if (BaseConstants.DOMESTIC_TYPE_INNER.equals(positionSnapshot.getDomesticType())) {
                            netAmountInSummary = netAmountInSummary.add(positionSnapshot.getNetAmount());
                            netPositionInSummary = netPositionInSummary.add(positionSnapshot.getNetWeight());
                        } else {
                            netAmountOutSummary = netAmountOutSummary.add(positionSnapshot.getNetAmount());
                            netPositionOutSummary = netPositionOutSummary.add(positionSnapshot.getNetQty());
                        }
                    }
                }
            }

            // 2. 境外汇总数据组装 (XAUUSD)
            SymbolPositionVo positionOut = new SymbolPositionVo();
            positionOut.setSymbol("XAUUSD");
            positionOut.setName("伦敦金(量化平盘)");
            positionOut.setNetAmount(netAmountOutSummary);
            positionOut.setNetPositionOunce(netPositionOutSummary);
            positionOut.setNetPosition(
                    netPositionOutSummary.multiply(BaseConstants.OUNCE_GRAM).setScale(2, BigDecimal.ROUND_HALF_UP));
            positionOut.setDomesticType(BaseConstants.DOMESTIC_TYPE_OUTER);

            Date now = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
            SimpleDateFormat forma = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat formatterTime = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
            positionOut.setSynDateTime(formatterTime.format(now));
            symbolPositionVoList.add(positionOut);

            // 3. 境内汇总数据组装 (AURBMB)
            SymbolPositionVo positionIn = new SymbolPositionVo();
            positionIn.setSymbol("AURBMB");
            positionIn.setName("克黄金/人民币(量化平盘)");
            positionIn.setNetAmount(netAmountInSummary);
            positionIn.setNetPositionOunce(netPositionInSummary);
            positionIn.setDomesticType(BaseConstants.DOMESTIC_TYPE_INNER);
            positionIn.setNetPosition(
                    netPositionInSummary.multiply(BaseConstants.OUNCE_GRAM).setScale(2, BigDecimal.ROUND_HALF_UP));
            // 计算成本均价
            positionIn.setAvgPrice(netAmountInSummary.divide(positionIn.getNetPosition(), 2, RoundingMode.HALF_UP));
            positionIn.setSynDateTime(formatterTime.format(now));
            positionIn.setMktPrice(BigDecimal.ZERO); // todo 核心价格
            positionIn.setProfitLoss(BigDecimal.ZERO); // todo 浮动盈亏
            positionIn.setUpdateDate(forma.format(now));
            positionIn.setUpdateTime(formatter.format(now));
            symbolPositionVoList.add(positionIn);

        } catch (Exception e) {
            logger.error("量化平盘头寸数据组装异常: ", e);
        }

        logger.info("量化平盘头寸数据组装完成--");
        return symbolPositionVoList;
    }

    /**
     * 获取量化平盘头寸信息（查询总敞口专用）
     */
    private Map<String, PositionSnapshot> getGoldHedgeInfo() {
        FolderPosition folderPosition = exposureManage.getFolderPosition("MgapHedge");
        if (null == folderPosition) {
            return null;
        }
        return folderPosition.getSymbolPositions();
    }

    private void handleSuccess() {
        if (isCircuitOpen) {
            isCircuitOpen = false;
        }
        failCount.set(0);
    }

    private void handleFail(Exception e) {
        lastFailTime = System.currentTimeMillis();
        if (isCircuitOpen) {
            logger.warn("探活失败，对端服务不可用，将在{}秒后重试.", REPLY_INTERVAL / 1000, e);
        } else {
            int count = failCount.incrementAndGet();
            logger.error("调用失败第{}次异常", count, e);

            if (count >= MAX_FAIL_COUNT) {
                isCircuitOpen = true;
                logger.error("连续失败次数触发熔断，转为每{}秒探测一次!", REPLY_INTERVAL / 1000, e);
            }
        }
    }
}
