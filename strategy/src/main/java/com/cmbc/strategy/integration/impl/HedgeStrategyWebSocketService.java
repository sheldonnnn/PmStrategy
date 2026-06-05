package com.cmbc.strategy.integration.impl;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.mds.distribution.PloyPrices;
import com.cmbc.oms.domain.exposure.dto.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.NewOrder;
import com.cmbc.strategy.constant.StrategyStatus;
import com.cmbc.strategy.domain.model.StrategyStatSummary;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.domain.model.hedge.GoldHedgeStrategyBean;
import com.cmbc.strategy.domain.model.hedge.GoldHedgeStrategyRunStatus;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;
import com.cmbc.strategy.integration.IHedgeStrategyWebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
、import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HedgeStrategyWebSocketService implements IHedgeStrategyWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(HedgeStrategyWebSocketService.class);

    // 发送策略事务信息
    private static final String SEND_GOLD_STRATEGY_INFO = "/strategy/data"; // 路径为 /user/czz/strategy/data
    private static final String SEND_GOLD_STRATEGY_STATUS = "/strategy/status"; // 路径为 /user/czz/strategy/status
    private static final String SEND_GOLD_STRATEGY_CHASE_WARN = "/strategy/chase/warn"; // 追单webSocket提示弹窗
    private static final String SEND_GOLD_STRATEGY_CHASE_TIMEOUT = "/strategy/chase/timeout/warn"; // 追单webSocket提示弹窗

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendGoldHedgeStrategyMap(String userName, String instanceId, Map<String, StrategyStatSummary> hdegeStrategyMap, HedgePositionSummary positionSummary, SymbolTimeSlice activeTimeSlice, List<NewOrder> newOrderList, PloyPrices ployPrice, Integer chaseNumber) {
        GoldStrategyBean goldStrategyBean = this.getGoldHedgeStrategyInstanceInfo(userName, instanceId, hdegeStrategyMap, positionSummary, activeTimeSlice, newOrderList, ployPrice, chaseNumber);
        log.debug("发送策略运行数据:{}", JSONObject.toJSONString(goldStrategyBean));
        // todo 3.// 消息发送到指定用户
        QueryVoBaseResponse response = new QueryVoBaseResponse(goldStrategyBean);
        messagingTemplate.convertAndSendToUser(goldStrategyBean.getUserName(), SEND_GOLD_STRATEGY_INFO, response);
    }

    @Override
    public GoldStrategyBean getGoldHedgeStrategyInstanceInfo(String userName, String instanceId, Map<String, StrategyStatSummary> hdegeStrategyMap, HedgePositionSummary positionSummary, SymbolTimeSlice activeTimeSlice, List<NewOrder> newOrderList, PloyPrices ployPrice, Integer chaseNumber) {
        if(StringUtils.isEmpty(instanceId)){
            return null;
        }
        HedgePositionVo hedgePositionVo = new HedgePositionVo();
        if(positionSummary != null){
            BigDecimal openPos = positionSummary.getFrozenNetPosition(); // 挂单占用头寸
            BigDecimal hedgedPos = positionSummary.getHedgedNetPosition(); //已平盘净头寸
            BigDecimal mgapHedgedPos = positionSummary.getMgapHedgedPosition();
            hedgePositionVo.setClientPosition(formatMoney(mgapHedgedPos)); //
            hedgePositionVo.setHedgedPosition(formatMoney(hedgedPos)); //
            hedgePositionVo.setActiveExposure(formatMoney(openPos)); //
            hedgePositionVo.setNetPosition(openPos == null ? new BigDecimal(0) : formatMoney(openPos.add(hedgedPos).add(mgapHedgedPos).add(openPos)));
            hedgePositionVo.setGoldClientPosition(formatMoney(positionSummary.getMgapClientPosition())); // 积存金刻盘头寸
            hedgePositionVo.setGoldClientPositionTime(positionSummary.getMgapClientPositionTime()); // 积存金盘头寸更新时间
        }

        GoldStrategyBean goldStrategyBean = new GoldStrategyBean();
        goldStrategyBean.setInstanceId(instanceId);
        goldStrategyBean.setUserName(userName);
        goldStrategyBean.setSymbol(activeTimeSlice.getSymbol());// 当前平盘合约品种
        List<HedgePositionVo> hedgePositionVoList = new ArrayList<>();

        hedgePositionVo.setHedgeTriggerPosition(formatMoney(activeTimeSlice.getTriggerLongPosition()));
        hedgePositionVo.setHedgeEndPosition(formatMoney(activeTimeSlice.getEndLongPosition())); // 策略平仓终止线
        hedgePositionVoList.add(hedgePositionVo);
        goldStrategyBean.setHedgePositionList(hedgePositionVoList);
        List<NewOrderVo> orderList = new ArrayList<>();
        goldStrategyBean.setNoCumQty(new BigDecimal(0));
        goldStrategyBean.setNoOrderNumber(0);
        // 订单信息组装开始
        if(!CollectionUtils.isEmpty(newOrderList)){
            BigDecimal noCumQty = BigDecimal.ZERO; // 未成交订单笔数
            // 循环组装订单开始
            goldStrategyBean.setNoOrderNumber(newOrderList.size());
            for (NewOrder order : newOrderList){
                noCumQty = noCumQty.add(order.getLeavesWeight(activeTimeSlice.getUnit()));
                NewOrderVo orderVo = new NewOrderVo();
                orderVo.setSide("BUY".equals(order.getSide()) ? "0" : "1");
                orderVo.setSymbol(order.getSymbol());
                orderVo.setOrderId(order.getOrderId()); // 订单编号
                orderVo.setOrderQty(order.getOrderQty());
                orderVo.setPrice(order.getPrice());
                orderVo.setOrderTime(null == order.getOrderTime() ? null : order.getOrderTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                orderVo.setChaseNumber(chaseNumber); // todo 追单次数
                orderVo.setStatus("0"); // 能查到的订单均为委托状态
                orderList.add(orderVo);
            }
            goldStrategyBean.setNoCumQty(noCumQty);
        }
        goldStrategyBean.setOrderList(orderList);
        // 行情信息组装
        List<DepthVo> depthList = new ArrayList<>();
        if(null != ployPrice){
            // 行情信息处理

            DepthVo depthVo3 = new DepthVo();
            depthVo3.setLevel(4); // 卖2
            if(ployPrice.getSecondBestAskPx() != null && ployPrice.getBestAskPxVolumeInfo().getTotalVolume() != null){
                depthVo3.setPrice(ployPrice.getSecondBestAskPx());
                depthVo3.setLevelQty(ployPrice.getBestAskPxVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo3);
            DepthVo depthVo2 = new DepthVo();
            depthVo2.setLevel(3); // 卖1
            if(ployPrice.getBestAskPx() != null && ployPrice.getBestAskVolumeInfo().getTotalVolume() != null){
                depthVo2.setPrice(ployPrice.getBestAskPx());
                depthVo2.setLevelQty(ployPrice.getBestAskVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo2);
            DepthVo depthVo = new DepthVo();
            depthVo.setLevel(1); // 买1
            if(ployPrice.getBestBidPx() != null && ployPrice.getBestBidVolumeInfo().getTotalVolume() != null){
                depthVo.setPrice(ployPrice.getBestBidPx());
                depthVo.setLevelQty(ployPrice.getBestBidVolumeInfo().getTotalVolume());
            }

            depthList.add(depthVo);
            DepthVo depthVo1 = new DepthVo();
            depthVo1.setLevel(2); // 买2
            if(ployPrice.getSecondBestBidPx() != null && ployPrice.getBestFidBidPxVolumeInfo().getTotalVolume() != null){
                depthVo1.setPrice(ployPrice.getSecondBestBidPx());
                depthVo1.setLevelQty(ployPrice.getBestFidBidPxVolumeInfo().getTotalVolume());
            }
            depthList.add(depthVo1);
        }
        goldStrategyBean.setDepthList(depthList);

        List<GoldHedgeStrategyBean> list = new ArrayList<>();
        if(!CollectionUtils.isEmpty(hdegeStrategyMap)) {
            // 循环便利的枚举信息组装
            for (StrategyStatSummary value : hdegeStrategyMap.values()) {
                // 数据组装开始
                GoldHedgeStrategyBean goldHedgeStrategyBean = new GoldHedgeStrategyBean();
                goldHedgeStrategyBean.setSymbol(value.getSymbol());
                goldHedgeStrategyBean.setSide("BUY".equals(value.getSide()) ? "0" : "1"); // 买卖方向
                goldHedgeStrategyBean.setDealAvgPrice(value.getAvgPrice()); // 成交均价
                goldHedgeStrategyBean.setDealQty(value.getCumQty()); // 成交数量
                goldHedgeStrategyBean.setDealWeight(formatMoney(value.getCumWeight()));
                if(BusinessConstant.DOMESTIC_TYPE_OUTER.equals(value.getDomesticType())){
                    if(null != value.getFxRate()){
                        goldHedgeStrategyBean.setFxRate(value.getFxRate());
                        goldHedgeStrategyBean.setDealAmount(value.getCumAmount().multiply(value.getFxRate()).setScale(2, BigDecimal.ROUND_HALF_UP));
                    }
                }else {
                    goldHedgeStrategyBean.setDealAmount(formatMoney(value.getCumAmount()));
                }
                list.add(goldHedgeStrategyBean);
            }
        }
        goldStrategyBean.setList(list);
        if (StringUtils.isEmpty(goldStrategyBean.getUserName())) {
            throw new RuntimeException("用户名为空,停止推送");
        }
        // 设置汇总信息
        GoldHedgeStrategyTotalBean bean = new GoldHedgeStrategyTotalBean();
        // 成交总重量
        BigDecimal dealTotalQty = BigDecimal.ZERO;
        // 成交总金额
        BigDecimal dealTotalPrice = BigDecimal.ZERO;
        BigDecimal profitTotalLoss = BigDecimal.ZERO; // 总浮动损益
        // 汇总信息设置
        for (GoldHedgeStrategyBean goldHedgeStrategyBean : goldStrategyBean.getList()) {
            // 成交总重量
            // 判断是买入还是卖出
            if ("0".equals(goldHedgeStrategyBean.getSide())) {
                dealTotalQty = dealTotalQty.add(null == goldHedgeStrategyBean.getDealWeight() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealWeight());
                // 成交总金额
                dealTotalPrice = dealTotalPrice.subtract(null == goldHedgeStrategyBean.getDealAmount() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealAmount());
            } else {
                // 卖出
                dealTotalQty = dealTotalQty.subtract(null == goldHedgeStrategyBean.getDealWeight() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealWeight());
                // 成交总金额
                dealTotalPrice = dealTotalPrice.add(null == goldHedgeStrategyBean.getDealAmount() ? BigDecimal.ZERO : goldHedgeStrategyBean.getDealAmount());
            }
        }
        bean.setDealTotalAmt(dealTotalPrice);
        bean.setDealTotalQty(dealTotalQty);
        bean.setProfitTotalLoss(profitTotalLoss);
        goldStrategyBean.setGoldHedgeStrategyTotalBean(bean);
        return goldStrategyBean;
    }

    @Override
    public void sendChasingRequest(String instanceId, String userName) {
        ChaseRequest bean = new ChaseRequest();
        bean.setInstanceId(instanceId);
        bean.setUserName(userName);
        bean.setMessage("实例:" + instanceId + " 追单触发,请及时处理!");
        // 触发追单告警信息到执行信息用户
        QueryVoBaseResponse response = new QueryVoBaseResponse(bean);
        messagingTemplate.convertAndSendToUser(userName, SEND_GOLD_STRATEGY_CHASE_WARN, response);
    }

    @Override
    public void sendChasingTimeOutWarning(String instanceId, String userName) {
        ChaseRequest bean = new ChaseRequest();
        bean.setInstanceId(instanceId);
        bean.setUserName(userName);
        bean.setMessage("实例:" + instanceId + " 追单处理超时!");
        // 触发追单告警信息到执行信息用户
        QueryVoBaseResponse response = new QueryVoBaseResponse(bean);
        messagingTemplate.convertAndSendToUser(userName, SEND_GOLD_STRATEGY_CHASE_TIMEOUT, response);
    }

    /**
     * 策略运行状态实时通知
     *
     * @param userName
     * @param instanceId
     * @param status
     */
    @Override
    public void sendGoldHedgeStrategyStatus(String userName, String instanceId, String status, String message) {
        // 参数校验
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(instanceId) || StringUtils.isEmpty(status)) {
            log.info("存在参数为空:userName:{},instanceId:{},status:{}", userName, instanceId, status);
            return;
        }
        GoldHedgeStrategyRunStatus bean = new GoldHedgeStrategyRunStatus();
        bean.setStatus(StrategyStatus.fromFinStatusCode(status));
        bean.setInstanceId(instanceId);
        bean.setUserName(userName);
        bean.setMessage(message);
        bean.setStatusMsg(StrategyStatus.fromFinStatusCode(status).getFinDescription());
        log.info("策略运行状态通知前端:{}", JSONObject.toJSONString(bean));
        // 开始进行webSocket请求推送
        messagingTemplate.convertAndSendToUser(userName, SEND_GOLD_STRATEGY_STATUS, new QueryVoBaseResponse(bean));
    }

    private BigDecimal formatMoney(BigDecimal num){
        if(num == null){
            return num;
        }
        return num.setScale(2, RoundingMode.HALF_UP);
    }
}