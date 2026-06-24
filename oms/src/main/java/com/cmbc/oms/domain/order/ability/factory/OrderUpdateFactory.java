package com.cmbc.oms.domain.order.ability.factory;

import com.cmbc.oms.domain.event.MgapIncrementalOrderEvent;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/2/26
 * @time 15:51
 * @description
 */
@Service
public class OrderUpdateFactory {
    
    public MgapIncrementalOrderEvent getIncrementalOrderManageEventFromOrderUpdate(ExecutionReport orderUpdate, String OpeningClosingType) {
        MgapIncrementalOrderEvent event = new MgapIncrementalOrderEvent();
        event.setOrderId(orderUpdate.getOrderId());
        event.setSymbol(orderUpdate.getSymbol());
        event.setPrice(orderUpdate.getPrice().doubleValue());
        event.setOrderQty(orderUpdate.getOrderQty()!=null?orderUpdate.getOrderQty().intValue():0);
        event.setLeaveQty(orderUpdate.getLeavesQty()!=null?orderUpdate.getLeavesQty().intValue():0);
        event.setDealPrice(orderUpdate.getLastPrice());
        event.setDealQty(orderUpdate.getLastQty()!=null?orderUpdate.getLastQty().intValue():0);
        event.setOrderType(orderUpdate.getOrdType());
        event.setOrderAttr(orderUpdate.getOrderAttr());
        event.setTradePurpose(orderUpdate.getTradePurpose());
        event.setType(orderUpdate.getType());  //todo
        if(BusinessConstant.DOMESTIC_TYPE_OUTER.equals(orderUpdate.getDomesticType())){
            event.setExchCode("");
        }else{
            event.setExchCode(orderUpdate.getExchId());
        }
        event.setExchCode(orderUpdate.getExchId());
        event.setBusinessType(orderUpdate.getBusinessType());
        event.setServiceId("OmsService"); // 必输项!
        event.setCounterParty(orderUpdate.getCounterParty()); // 需要根据实际情况设置
        event.setDataSource(orderUpdate.getDataSource());
        event.setLocalOrderNo(orderUpdate.getLocalOrderNo());
        event.setSysOrderNo(orderUpdate.getExchOrderId());
        event.setMatchNo(orderUpdate.getExecId());
        event.setStrategyId(orderUpdate.getStrategyId());
        event.setInstanceId(orderUpdate.getInstanceId());
        event.setTraderNo(orderUpdate.getTraderNo());
        event.setUserName(orderUpdate.getUserName());
        event.setMemberId(orderUpdate.getMemberId());
        event.setClientId(orderUpdate.getClientId());
        event.setInventoryType(orderUpdate.getInventoryType());
        event.setDomesticType(orderUpdate.getDomesticType());
        event.setSide(orderUpdate.getSide());
        event.setEoFlag(orderUpdate.getEoFlag());
        event.setShFlag(orderUpdate.getShFlag());
        Map<String, String> extra = new HashMap<>();
        extra.put("orderStatus", orderUpdate.getStatusMsg());
        extra.put("orderStatusCode", orderUpdate.getApamaStatus());//这里发给apama，需要使用原始状态码
        extra.put("OrderDate", orderUpdate.getOrderDate());
        extra.put("OrderTime", orderUpdate.getOrderTime().toString());
        extra.put("Level", orderUpdate.getLevel());
        extra.put("matchDate", orderUpdate.getMatchDate()==null?"":orderUpdate.getMatchDate());
        extra.put("matchTime", orderUpdate.getMatchTime()==null?"":orderUpdate.getMatchTime().toString());
        
        // 保证apama持仓，头寸损益计算更新生效增加额外参数
        extra.put("ContractInfo", "");
        extra.put("ExchCode", orderUpdate.getExchId()); //交易所代码
        extra.put("Unit", orderUpdate.getUnit().toString());//每手乘数
        extra.put("MeasureUnit", "");//计价单位
        extra.put("Tick", orderUpdate.getTick());
        extra.put("VarietyId", "".equals(orderUpdate.getVarietyId())?"au":orderUpdate.getVarietyId());
        extra.put("InventoryType", orderUpdate.getInventoryType());
        extra.put("DomesticType", orderUpdate.getDomesticType());
        extra.put("EndDeliveryDate", orderUpdate.getEndDeliveryDate());
        extra.put("IsHistoryContract", orderUpdate.getIsHistoryContract());
        extra.put("accuracy", orderUpdate.getAccuracy());//精度
        extra.put("stepPosition", "0");
        extra.put("StraightDiscForkType", "");
        extra.put("ForkPlate", "");
        extra.put("PositionTagCode", orderUpdate.getTagCode());
        extra.put("PositionTagName", orderUpdate.getTagName());
        extra.put("OpeningClosingType", OpeningClosingType);
        extra.put("Currency", orderUpdate.getCurrency());
        extra.put("settleTime", "");
        extra.put("SystemId", orderUpdate.getSystemId());
        
        event.setExtraParas(extra);
        return event;
    }
}
