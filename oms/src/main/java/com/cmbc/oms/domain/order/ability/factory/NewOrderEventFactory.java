package com.cmbc.oms.domain.order.ability.factory;

import com.apama.util.StringUtils;
import com.cmbc.oms.domain.event.CancelOrderEvent;
import com.cmbc.oms.domain.event.NewOrderEvent;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import com.cmbc.oms.infrastructure.util.DateUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 19:14
 * @description
 */
@Component
public class NewOrderEventFactory {

    public NewOrderEvent createNewOrderEvent(NewOrder newOrder){
        NewOrderEvent newOrderEvent = new NewOrderEvent();
        // 1.基础要素
        newOrderEvent.setOrderId(newOrder.getOrderId()); // 订单ID
        newOrderEvent.setSymbol(newOrder.getSymbol()); // 合约
        newOrderEvent.setPrice(newOrder.getPrice()); // 价格
        newOrderEvent.setSide(newOrder.getSide()); // 买卖方向
        newOrderEvent.setType(newOrder.getType()); // 订单类型 Limit等
        newOrderEvent.setQuantity(newOrder.getOrderQty().intValue()); // 数量
        newOrderEvent.setServiceId("OmsRiskService"); // 服务ID Java订单发送至apama-oms 的固定id
        newOrderEvent.setBrokerId("");
        newOrderEvent.setBookId("");
        newOrderEvent.setMarketId("");

        // 2.扩展要素
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("38", newOrder.getOrderQty().toString()); // 订单数量
        extraParams.put("BusinessType", newOrder.getBusinessType()); // 业务类型
        extraParams.put("CREDIT_VALIDATE_COMPLETE", "true");

        extraParams.put("CreditResult", "true");
        extraParams.put("Currency", StringUtils.isEmptyString(newOrder.getCurrency()) ? "" : newOrder.getCurrency());
        // 境外-策略提供, 境内 空
        extraParams.put("DomesticType", newOrder.getDomesticType()); // 境内-0, 境外-1
        extraParams.put("EndDeliveryDate", ""); //终止交割日期
        extraParams.put("EoFlag", newOrder.getEoFlag()); //开平标志
        extraParams.put("ExchCode", newOrder.getExchCode()); //交易所代码
        extraParams.put("ForkPlate", ""); // 叉盘币种
        extraParams.put("InventoryType", newOrder.getInventoryType()); // 合约类型 现货期货
        extraParams.put("IsHistoryContract", "false"); // 是否历史合约
        extraParams.put("Level", "0"); //todo
        extraParams.put("LocalOrderNo", newOrder.getLocalOrderNo()); // 本地订单号
        extraParams.put("OMS_IssueTime", ""); //OMS发送时间
        extraParams.put("OrderAttr", "0"); // 订单属性
        extraParams.put("OrderDate", DateUtil.getCurrentDate()); // 委托日期
        extraParams.put("OrderTimeStamp", DateUtil.getCurrentDateTime()); // 委托时间戳
        extraParams.put("OrderType", newOrder.getType()); // 订单类型
        extraParams.put("PositionTagCode", newOrder.getPositionTagCode()); // 头寸标签编码
        extraParams.put("PositionTagName", newOrder.getPositionTagName()); // 头寸标签名称
        extraParams.put("ShFlag", newOrder.getShFlag()); // 投保标志
        extraParams.put("StraightDisc", ""); // 直盘币种
        extraParams.put("StraightDiscForkType", ""); // 直盘叉盘区分类型 todo
        extraParams.put("StrategyID", newOrder.getStrategyInstanceID()); // 策略ID
        extraParams.put("StrategyInstanceID", newOrder.getStrategyInstanceID()); // 策略实例ID
        extraParams.put("Symbol", newOrder.getSymbol()); // 合约
        extraParams.put("SystemId", "PM");
        extraParams.put("Tick",".01"); //最小价位
        extraParams.put("TradePurpose", newOrder.getTradePurpose()); // 交易目的
        
        extraParams.put("Type", "2");    // 默认 3 . 平盘策略订单 todo
        extraParams.put("Unit", String.valueOf(newOrder.getUnit())); //每手乘数
        extraParams.put("UserName", newOrder.getUserName()); // 用户名
        extraParams.put("VarietyId", newOrder.getVarietyId()); // 品种代码
        extraParams.put("accuracy", String.valueOf(newOrder.getAccuracy())); // 精度
        extraParams.put("stepPosition", String.valueOf(newOrder.getStepPosition())); // 步长
        //境外订单处理
        if("1".equals(newOrder.getDomesticType())){
            newOrderEvent.setExchange(newOrder.getExchCode() + "-FIX"); // 交易所 境内：上海黄金交易所 02， 上期所 01，境外交易市场代码
            extraParams.put("15", newOrder.getCurrency());
            extraParams.put("OrderTime", DateUtil.getAboradDateTime()); // 境外委托时间
            extraParams.put("TraderNo", newOrder.getUserName()); // 交易员 境内dimple账户，境外是 外资行账户
            extraParams.put("MeasureUnit", "美元/盎司"); // 计价单位
            extraParams.put("CounterParty", newOrder.getCounterParty());
            extraParams.put("dataSource", newOrder.getExchCode() + "-FIX"); // 数据源
            extraParams.put("omsSendServiceId", newOrder.getExchCode() + "-FIX"); // 境内外服务id
            extraParams.put("ExpiredTime", "0"); // 过期时间
            extraParams.put("117", newOrder.getQuoteEntryId());
            
        }else {
            newOrderEvent.setExchange(newOrder.getExchCode()); // 交易所 境内：上海黄金交易所02， 上期所 01，境外交易市场代码
            newOrderEvent.setOwnerId(newOrder.getTraderNo()); // 交易员 境内dimple账户，境外是 空
            extraParams.put("OrderTime", DateUtil.getCurrentTime()); // 委托时间
            extraParams.put("TraderNo", newOrder.getTraderNo()); // 交易员 境内dimple账户，境外是 外资行账户
            extraParams.put("ClientId", newOrder.getClientId()); // 客户号
            extraParams.put("MemberId", newOrder.getMemberId()); // 席位号
            extraParams.put("dataSource", "DIMPLE"); // 数据源
            extraParams.put("ExpiredTime", newOrder.getExpiredTime().toString()); // 过期时间
        }
        
        newOrderEvent.setExtraParams(extraParams);
        
        //3.newOrder事件返回
        return newOrderEvent;
    }
    
    public CancelOrderEvent createCancelOrderEvent(NewOrder newOrder,String reason){
        CancelOrderEvent cancelOrderEvent = new CancelOrderEvent();
        // 1.基础要素
        cancelOrderEvent.setOrderId(newOrder.getOrderId()); // 订单ID
        cancelOrderEvent.setServiceId("OmsService"); // 服务ID java订单发送至apama-oms 的固定id
        // 2.扩展要素
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put(BusinessConstant.ERROR_ID_NAME, BusinessConstant.ENTRUST_EXPIRE_TIME);
        extraParams.put(BusinessConstant.ERROR_MSG_NAME, reason);
        //境外无撤单处理
        cancelOrderEvent.setExtraParams(extraParams);
        
        //3.cancelOrderEvent事件返回
        return cancelOrderEvent;
    }
}
