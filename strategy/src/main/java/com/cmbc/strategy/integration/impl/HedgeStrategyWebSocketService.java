package com.cmbc.strategy.integration.impl;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesLatchedCacheService;
import com.cmbc.mds.forex.quotes.dto.LatchedQuoteWrapper;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.oms.domain.exposure.model.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.Entity.NewOrder;
import com.cmbc.oms.infrastructure.util.DateUtil;
import com.cmbc.strategy.domain.dto.baseRes.QueryVoBaseResponse;
import com.cmbc.strategy.domain.model.config.StrategyStatSummary;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.domain.model.hedge.GoldHedgeStrategyBean;
import com.cmbc.strategy.domain.model.hedge.GoldHedgeStrategyRunStatus;
import com.cmbc.strategy.domain.model.hedge.GoldHedgeStrategyTotalBean;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;
import com.cmbc.strategy.domain.vo.DepthVo;
import com.cmbc.strategy.domain.vo.HedgePositionVo;
import com.cmbc.strategy.domain.vo.NewOrderVo;
import com.cmbc.strategy.integration.IHedgeStrategyWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author: 崔健
 * @Date: 2026/03/02  16:15
 * @Description:
 */
@Service
public class HedgeStrategyWebSocketService implements IHedgeStrategyWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(HedgeStrategyWebSocketService.class);
    // 发送策略实例信息
    private static final String SEND_GOLD_STRATEGY_INFO = "/strategy/data/"; // 路径为 /user/czz/strategy/data
    private static final String SEND_GOLD_STRATEGY_STATUS = "/strategy/status/"; // 路径为 /user/czz/strategy/status
    private static final String SEND_GOLD_STRATEGY_CHASE_WARN = "/strategy/chase/warn/"; // 追单webSocket提示弹窗
    private static final String SEND_GOLD_STRATEGY_CHASE_TIMEOUT = "/strategy/chase/timeout/warn/"; // 追单webSocket提示弹窗

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MergeQuotesLatchedCacheService mergeQuotesLatchedCacheService;

    @Override
    public void sendGoldHedgeStrategyMap(String userName, String instanceId, Map<String, StrategyStatSummary> hedgeStrategyMap,
                                         HedgePositionSummary positionSummary, SymbolTimeSlice activeTimeSlice,
                                         List<NewOrder> newOrderList, PloyPrices ployPrice, Integer chaseNumber) {
        GoldStrategyBean goldStrategyBean = this.getGoldHedgeStrategyInstanceInfo(userName, instanceId, hedgeStrategyMap,
                positionSummary, activeTimeSlice, newOrderList, ployPrice, chaseNumber);
        log.debug("发送策略运行数据: {}", JSONObject.toJSONString(goldStrategyBean));
        // todo 3.// 消息发到前端空用户
        QueryVoBaseResponse response = new QueryVoBaseResponse(goldStrategyBean);
        messagingTemplate.convertAndSendToUser(goldStrategyBean.getUserName(), SEND_GOLD_STRATEGY_INFO, response);
    }

    @Override
    public GoldStrategyBean getGoldHedgeStrategyInstanceInfo(String userName, String instanceId,
                                                             Map<String, StrategyStatSummary> hedgeStrategyMap,
                                                             HedgePositionSummary positionSummary,
                                                             SymbolTimeSlice activeTimeSlice,
                                                             List<NewOrder> newOrderList, PloyPrices ployPrice,
                                                             Integer chaseNumber) {
        if (StringUtils.isEmpty(instanceId)) {
            return null;
        }
        HedgePositionVo hedgePositionVo = new HedgePositionVo();
        GoldStrategyBean goldStrategyBean = new GoldStrategyBean();
        if (positionSummary != null) {
            BigDecimal openPos = positionSummary.getFrozenNetPosition(); // 挂单占用头寸
            BigDecimal hedgedPos = positionSummary.getHedgedNetPosition(); // 已平盘净头寸
            BigDecimal mgapHedgedPos = positionSummary.getMgapHedgedPosition();
            BigDecimal mgapClientPosition = positionSummary.getMgapClientPosition();
            hedgePositionVo.setClientPosition(formatMoney(mgapHedgedPos));
            hedgePositionVo.setHedgedPosition(formatMoney(hedgedPos));
            hedgePositionVo.setActiveExposure(formatMoney(openPos));
            hedgePositionVo.setNetPosition(openPos == null ? new BigDecimal("0") :
                    formatMoney(openPos.add(hedgedPos).add(mgapHedgedPos).add(mgapClientPosition)));
            goldStrategyBean.setGoldClientPosition(formatMoney(mgapClientPosition)); // 积存金到盘头寸
            hedgePositionVo.setGoldClientPositionTime(positionSummary.getUpdateTime()); // 积存金容盘头寸更新时间
            goldStrategyBean.setClientPrice(positionSummary.getMgapClientPrice());
            goldStrategyBean.setClientPriceTime(positionSummary.getUpdateTime());
        }

        goldStrategyBean.setInstanceId(instanceId);
        goldStrategyBean.setUserName(userName);
        goldStrategyBean.setSymbol(activeTimeSlice.getSymbol()); // 当前平盘合约代码
        LatchedQuoteWrapper fxWrapper =
                mergeQuotesLatchedCacheService.getSystemInitLatchedPriceBySymbol("USD/CNH", null);
        if (null != fxWrapper) {
            PloyPrices fxPrice = fxWrapper.getData();
            if (null != fxPrice) {
                goldStrategyBean.setFxSymbol(fxPrice.getMktPx());
                goldStrategyBean.setFxSymbolTime(DateUtil.longToDate(fxWrapper.getLastUpdateTime(), "yyyyMMdd HH:mm:ss"));
            }
        }

        List<HedgePositionVo> hedgePositionVoList = new ArrayList<>();

        hedgePositionVo.setHedgeTriggerPosition(formatMoney(activeTimeSlice.getTriggerLongPosition()));
        hedgePositionVo.setHedgeEndPosition(formatMoney(activeTimeSlice.getEndLongPosition())); // 策略平盘终止线
        hedgePositionVoList.add(hedgePositionVo);
        goldStrategyBean.setHedgePositionList(hedgePositionVoList);
        List<NewOrderVo> orderList = new ArrayList<>();
        goldStrategyBean.setNoCumQty(new BigDecimal("0"));
        goldStrategyBean.setNoOrderNumber(0);
        // 订单信息组装省略...
        // ...
        goldStrategyBean.setOrderList(orderList);
        // 行情信息处理
        List<DepthVo> depthList = new ArrayList<>();
        if (null != ployPrice) {
            // 行情信息处理
            DepthVo depthVo3 = new DepthVo();
            depthVo3.setLevel(4); // 卖二
            if (ployPrice.getSecondBestAskPx() != null && ployPrice.getSecondBestAskVolumeInfo().getTotalVolume() != null) {
                depthVo3.setPrice(ployPrice.getSecondBestAskPx());
                depthVo3.setLevelQty(ployPrice.getSecondBestAskVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo3);
            DepthVo depthVo2 = new DepthVo();
            depthVo2.setLevel(3); // 卖一
            if (ployPrice.getBestAskPx() != null && ployPrice.getBestAskVolumeInfo().getTotalVolume() != null) {
                depthVo2.setPrice(ployPrice.getBestAskPx());
                depthVo2.setLevelQty(ployPrice.getBestAskVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo2);
            DepthVo depthVo = new DepthVo();
            depthVo.setLevel(1); // 买一
            if (ployPrice.getBestBidPx() != null && ployPrice.getBestBidVolumeInfo().getTotalVolume() != null) {
                depthVo.setPrice(ployPrice.getBestBidPx());
                depthVo.setLevelQty(ployPrice.getBestBidVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo);
            DepthVo depthVo1 = new DepthVo();
            depthVo1.setLevel(2); // 买二
            if (ployPrice.getSecondBestBidPx() != null && ployPrice.getSecondBestBidVolumeInfo().getTotalVolume() != null) {
                depthVo1.setPrice(ployPrice.getSecondBestBidPx());
                depthVo1.setLevelQty(ployPrice.getSecondBestBidVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo1);
        }
        goldStrategyBean.setDepthList(depthList);
        // 成交总金额
        BigDecimal dealTotalQty = BigDecimal.ZERO;
        // 成交总金额
        BigDecimal dealTotalPrice = BigDecimal.ZERO;
        BigDecimal profitToLoss = BigDecimal.ZERO; // 总浮动损益
        List<GoldHedgeStrategyBean> list = new ArrayList<>();
        if (!CollectionUtils.isEmpty(hedgeStrategyMap)) {
            // 循环使用策略信息组装
            for (StrategyStatSummary value : hedgeStrategyMap.values()) {
                // 数据组装开始
                GoldHedgeStrategyBean goldHedgeStrategyBean = new GoldHedgeStrategyBean();
                goldHedgeStrategyBean.setSymbol(value.getSymbol());
                goldHedgeStrategyBean.setSide("BUY".equals(value.getSide()) ? "0" : "1"); // 买卖方向
                goldHedgeStrategyBean.setDealAvgPrice(value.getAvgPrice()); // 成交均价
                goldHedgeStrategyBean.setDealQty(value.getCumQty()); // 成交数量
                goldHedgeStrategyBean.setDealWeight(formatMoney(value.getCumWeight())); // 成交重量
                // 成交数量
                // 判断是买入还是卖出
                if ("BUY".equals(value.getSide())) {
                    dealTotalQty = dealTotalQty.add(null == goldHedgeStrategyBean.getDealWeight() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealWeight());
                    // 成交总金额
                    // dealTotalPrice = dealTotalPrice.add(null == goldHedgeStrategyBean.getDealAmount() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealAmount());
                } else {
                    // 卖出
                    dealTotalQty = dealTotalQty.subtract(null == goldHedgeStrategyBean.getDealWeight() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealWeight());
                    // 成交总金额
                    // dealTotalPrice = dealTotalPrice.subtract(null == goldHedgeStrategyBean.getDealAmount() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealAmount());
                }
                // if(BusinessConstant.DOMESTIC_TYPE_OUTER.equals(value.getDomesticType())){
                //    if(null != value.getFxRate()){
                //        goldHedgeStrategyBean.setFxRate(value.getFxRate());
                //        goldHedgeStrategyBean.setDealAmount(value.getCumAmount().multiply(value.getFxRate()).setScale(2, BigDecimal.ROUND_HALF_UP));
                //    }
                // } else {
                //    goldHedgeStrategyBean.setDealAmount(formatMoney(value.getCumAmount()));
                // }
                list.add(goldHedgeStrategyBean);
            }
        }
        goldStrategyBean.setList(list);
        if (StringUtils.isEmpty(goldStrategyBean.getUserName())) {
            throw new RuntimeException("用户名为空, 停止推送");
        }
        
        // 汇总信息总置
        GoldHedgeStrategyTotalBean bean = new GoldHedgeStrategyTotalBean();
        
        // 汇总信息总置
        for (GoldHedgeStrategyBean goldHedgeStrategyBean : goldStrategyBean.getList()) {
            // 成交重量
            // 判断是买入还是卖出
            if ("0".equals(goldHedgeStrategyBean.getSide())) {
                dealTotalQty = dealTotalQty.add(null == goldHedgeStrategyBean.getDealWeight() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealWeight());
                // 成交总金额
                // dealTotalPrice = dealTotalPrice.add(null == goldHedgeStrategyBean.getDealAmount() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealAmount());
            } else {
                // 卖出
                dealTotalQty = dealTotalQty.subtract(null == goldHedgeStrategyBean.getDealWeight() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealWeight());
                // 成交总金额
                // dealTotalPrice = dealTotalPrice.add(null == goldHedgeStrategyBean.getDealAmount() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealAmount());
            }
        }
        
        // bean.setTotalQty(dealTotalQty);
        // bean.setProfitToLoss(profitToLoss);
        goldStrategyBean.setGoldHedgeStrategyTotalBean(bean);
        return goldStrategyBean;
    }

    @Override
    public void sendChaseingRequest(String instanceId, String userName) {
        
    }

    @Override
    public void sendGoldHedgeStrategyStatus(String userName, String instanceId, String status, String message) {
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(instanceId) || StringUtils.isEmpty(status)) {
            log.info("存在参数为空 userName:{} instanceId:{} status:{}", userName, instanceId, status);
            return;
        }
        GoldHedgeStrategyRunStatus bean = new GoldHedgeStrategyRunStatus();
        bean.setStatus(StrategyStatus.fromFinStatusCode(status));
        bean.setInstanceId(instanceId);
        bean.setUserName(userName);
        bean.setMessage(message);
        bean.setStatusMsg(StrategyStatus.fromFinStatusCode(status).getFinDescription());
        log.info("策略运行状态推送前端: {}", JSONObject.toJSONString(bean));
        messagingTemplate.convertAndSendToUser(userName, SEND_GOLD_STRATEGY_STATUS, new QueryVoBaseResponse(bean));
    }

    private BigDecimal formatMoney(BigDecimal num) {
        if (num == null) {
            return null;
        }
        return num.setScale(2, RoundingMode.HALF_UP);
    }
}
