package com.cmbc.oms.domain.exposure.service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.cmbc.oms.domain.order.model.ContractInfoBasic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class MgapClientPositionManage {

    private final static Logger logger = LoggerFactory.getLogger(MgapClientPositionManage.class);

//    private final Map<String, PositionSummary> mgapPositionCache = new ConcurrentHashMap<>(); // 积存金平盘全局汇总头寸数据缓存
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
    private Map<String, GoldPrice> priceCache = new HashMap<>(); // 行情数据缓存

    /**
     * 定时从积存金系统查询客盘头寸
     */
    @Scheduled(fixedRate = 1000)
    public void syncMgapPosition() {
        try {
            if (isCircuitOpen) {
                if (System.currentTimeMillis() - lastFailTime < REPLY_INTERVAL) {
                    logger.info("--- 熔断期间，不做处理 ---");
                    return;
                }
            }

            MgapPosResponse mgapPosResponse = restTemplate.postForObject(url, null, MgapPosResponse.class);
//            logger.info("积存金头寸查询结果返回结果:code={}, {}", mgapPosResponse.getReturnCode().getType(), JSONObject.toJSONString(mgapPosResponse.getTotalAll()))

            if ("S".equals(mgapPosResponse.getReturnCode())) {
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
        try {
            // 获取客盘头寸数据
            PositionDataResponse mgapPositionInfo = getMgapPositionSummary(fxSymbol);
            // 获取hedged头寸数据
            PositionDataResponse hedgedPosition = getHedgedPosition(fxSymbol);

            PositionSummary hedgePositionSummary = hedgedPosition.getPositionSummary(); // quant汇总数据
            PositionSummary mgapPositionSummary = mgapPositionInfo.getPositionSummary(); // 积存金头寸汇总数据

            // 积存金头寸统计完成，合并到汇总对象
            BigDecimal totalPL = (mgapPositionSummary.getProfitAndLoss() != null ? mgapPositionSummary.getProfitAndLoss() : BigDecimal.ZERO)
                    .add(hedgePositionSummary.getProfitAndLoss() != null ? hedgePositionSummary.getProfitAndLoss() : BigDecimal.ZERO);
            hedgePositionSummary.setProfitAndLoss(totalPL);

            BigDecimal totalNetPos = (mgapPositionSummary.getNetPosition() != null ? mgapPositionSummary.getNetPosition() : BigDecimal.ZERO)
                    .add(hedgePositionSummary.getNetPosition() != null ? hedgePositionSummary.getNetPosition() : BigDecimal.ZERO);
            hedgePositionSummary.setNetPosition(totalNetPos);

            BigDecimal totalNetAmt = (mgapPositionSummary.getNetAmount() != null ? mgapPositionSummary.getNetAmount() : BigDecimal.ZERO)
                    .add(hedgePositionSummary.getNetAmount() != null ? hedgePositionSummary.getNetAmount() : BigDecimal.ZERO);
            hedgePositionSummary.setNetAmount(totalNetAmt);

            if (BigDecimal.ZERO.compareTo(hedgePositionSummary.getNetPosition()) != 0) {
                // 计算均价：均价 = 净敞口金额 / 净敞口重量 (取绝对值转为负数计算)
                BigDecimal avgPrice = hedgePositionSummary.getNetAmount().negate()
                        .divide(hedgePositionSummary.getNetPosition(), 2, RoundingMode.HALF_UP);
                hedgePositionSummary.setAvgPrice(avgPrice);
            }

            hedgedPosition.setMgapPosition(mgapPositionInfo.getMgapPosition());
            hedgedPosition.setConnected(this.isConnected);
            return hedgedPosition;

        } catch (Exception e) {
            logger.error("获取积存金头寸汇总数据异常!!", e);
        }
        return null;
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
            MgapPosResponse mgapPosResponse = restTemplate.postForObject(url, null, MgapPosResponse.class);
            if (Objects.isNull(mgapPositionSummary) || !isValid(mgapPositionSummary.getUpdateTime())) {
                return null;
            }
        }

        BigDecimal mgapPosition = mgapPositionSummary.getNetPosition();
        PositionSnapshot quantPosition = exposureManage.getTotalPosition("MgapHedge");

        BigDecimal hedgedPosition = quantPosition.getNetWeight();
        BigDecimal frozenPosition = quantPosition.getFrozenWeight();

        strategyPosition.setMgapNetPosition(mgapPosition);
        strategyPosition.setHedgedNetPosition(hedgedPosition);
        strategyPosition.setFrozenNetPosition(frozenPosition);
        strategyPosition.setUpdateTime(mgapPositionSummary.getUpdateTime());

        return strategyPosition;
    }

    /**
     * 按接口查询积存金头寸汇总信息数据
     */
    public PositionDataResponse getMgapPositionSummary(String fxSymbol) {
        PositionDataResponse mgapPositionResponse = new PositionDataResponse();
        List<PositionVo> mgapPositionList = new ArrayList<>();
        PositionSummary positionSummary = new PositionSummary();

        BigDecimal netPositionSummary = BigDecimal.ZERO;
        BigDecimal netDealAmountSummary = BigDecimal.ZERO;
        BigDecimal profitLossSummary = BigDecimal.ZERO;

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

            String depthTime = StringUtils.isEmpty(value.getPriceTime()) ? null : value.getPriceTime().replaceAll("-", "");
            mgapPosition.setDepthUpdateTime(depthTime);
            mgapPosition.setProfitLoss(value.getUnrealizedPL()); // 浮动盈亏
            mgapPosition.setAvgPrice(value.getPrice()); // 成本价
            mgapPosition.setMktPrice(value.getMktPrice()); // 市场最新价格

            positionSummary.setUpdateTime(value.getPositionTime());

            // 针对特定币种进行汇总统计
            if (("KAURMB".equals(key) || "AURMB".equals(key) || "PGCRMB".equals(key))) {
                profitLossSummary = profitLossSummary.add(value.getUnrealizedPL()).setScale(2, RoundingMode.DOWN);
                netPositionSummary = netPositionSummary.add(value.getQty());
                netDealAmountSummary = netDealAmountSummary.add(value.getAmt());
            } else if ("XAUUSD".equals(key)) {
                // 伦敦金转换逻辑
                netPositionSummary = netPositionSummary.add(value.getQty().multiply(BaseConstants.OUNCE_GRAM)).setScale(4, RoundingMode.HALF_UP);

                PloyPrices fxRatePrice = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(fxSymbol);
                if (fxRatePrice != null && fxRatePrice.getMidPx() != null) {
                    BigDecimal fxRate = fxRatePrice.getMidPx();
                    netDealAmountSummary = netDealAmountSummary.add(value.getAmt().multiply(fxRate)).setScale(2, RoundingMode.HALF_UP);
                    // todo 浮动盈亏计算...
                }
            }
            mgapPositionList.add(mgapPosition);
        }

        positionSummary.setProfitAndLoss(profitLossSummary);
        positionSummary.setSymbol("KAURMB");
        positionSummary.setNetPosition(netPositionSummary);
        positionSummary.setNetAmount(netDealAmountSummary);

        if (BigDecimal.ZERO.compareTo(netPositionSummary) != 0) {
            BigDecimal avgPrice = netDealAmountSummary.negate()
                    .divide(netPositionSummary, 2, RoundingMode.HALF_UP);
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

        Map<String, PositionSnapshot> symbolPositions = getGoldHedgeInfo();
        if (null != symbolPositions) {
            int id = 1;
            for (PositionSnapshot positionSnapshot : symbolPositions.values()) {
                synchronized (positionSnapshot) {
                    PositionVo info = new PositionVo();
                    info.setId(id++);
                    info.setSymbol(positionSnapshot.getSymbol());

                    ContractInfoBasic contractInfoBasic = basicParamCacheManager.getContractInfo(positionSnapshot.getSymbol());

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
                            PloyPrices ployPrice = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(positionSnapshot.getSymbol());
                            PloyPrices basePrice = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol("Au(T+D)");

                            if (ployPrice != null && basePrice != null && ployPrice.getMidPx() != null && basePrice.getMidPx() != null) {
                                // 计算基准价差：(当前价 - Au(T+D)价)，保留2位小数，四舍五入
                                info.setBaseSpread(ployPrice.getMidPx().subtract(basePrice.getMidPx()).setScale(2, BigDecimal.ROUND_HALF_UP));
                                // 计算价差盈亏：价差 * 净头寸，保留2位小数
                                info.setSpreadPL(info.getBaseSpread().multiply(info.getNetPosition()).setScale(2, BigDecimal.ROUND_HALF_UP));
                            }
                            }
                        } else {
                            // 非内盘/期货的处理逻辑
                            info.setNetPosition(positionSnapshot.getNetQty());
                            // 净头寸换算（可能涉及盎司到克的转换）
                            info.getNetPosition().multiply(BaseConstants.OUNCE_GRAM); // 净敞口头寸

                            netPositionSummary = netPositionSummary.add(info.getNetPosition());

                            // 获取汇率或基准价进行折算
                            if (mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(fxSymbol) != null) {
                                // TODO 1.CNH or CNY? 2.处理逻辑补充
                                BigDecimal fxRate = mergeQuotesCacheService.getSystemInitPloyPriceBySymbol(fxSymbol).getMidPx();

                                // 净成交金额和盈亏按汇率折算
                                netDealAmountSummary = netDealAmountSummary.add(info.getNetAmount().multiply(fxRate)).setScale(2, BigDecimal.ROUND_HALF_UP);
                                profitLossSummary = profitLossSummary.add(info.getProfitLoss().multiply(fxRate)).setScale(2, BigDecimal.ROUND_HALF_UP);

//                                logger.info("positionSnapshot:{}", JSONObject.toJSONString(positionSnapshot));
//                                logger.info("outer symbol:{}, qty:{}, amt:{}, netPositionSummary:{}, fxRate:{}",
                                        positionSnapshot.getSymbol(), positionSnapshot.getNetQty(), info.getNetAmount(), netPositionSummary, fxRate);

                                positionSummary.setFxRate(fxRate);
                            }
                        }
                    hedgedPositionList.add(info);
                    }
                }
            }

        positionSummary.setProfitAndLoss(profitLossSummary);
        positionSummary.setNetPosition(netPositionSummary);
        positionSummary.setNetAmount(netDealAmountSummary);
        positionSummary.setSymbol("KAURMB");

        if (BigDecimal.ZERO.compareTo(positionSummary.getNetPosition()) != 0) {
            BigDecimal avgPrice = positionSummary.getNetAmount().negate()
                    .divide(positionSummary.getNetPosition(), 2, RoundingMode.HALF_UP);
            positionSummary.setAvgPrice(avgPrice);
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        positionSummary.setUpdateTime(formatter.format(new Date()));

        hedgedPositionResponse.setHedgedPosition(hedgedPositionList);
        hedgedPositionResponse.setPositionSummary(positionSummary);

        return hedgedPositionResponse;
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

    /**
     * 校验头寸更新时间是否超过1s (此处逻辑为2000ms)
     */
    public static boolean isValid(String updateTime) {
        if (StringUtils.isEmpty(updateTime)) {
            return true;
        }
        long timeMill = DateUtil.getTimeMill(updateTime);
        long now = System.currentTimeMillis();
        if (now - timeMill < 2000) {
            return true;
        }
        return false;
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

