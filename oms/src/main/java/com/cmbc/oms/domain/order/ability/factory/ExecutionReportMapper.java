package com.cmbc.oms.domain.order.ability.factory;

import com.cmbc.oms.domain.event.OrderUpdateEvent;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.PmOrderEntity;
import com.cmbc.oms.domain.order.model.entity.PmTradeEntity;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class ExecutionReportMapper {

    @Autowired
    private BasicParamCacheManager basicParamCacheManager;
    
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS");
    public static final DateTimeFormatter MATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
    public static final DateTimeFormatter MATCH_TIME_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

    public ExecutionReport toBo(OrderUpdateEvent event) {
        ExecutionReport executionReport = new ExecutionReport();
        Map<String, String> extraParams = event.getExtraParams();
        executionReport.setExchOrderId(event.getOrderId());
        executionReport.setExchOrderId(event.getSysOrderNo());
        executionReport.setLocalOrderNo(extraParams.get("LocalOrderNo"));
        executionReport.setOrderId(event.getOrderId()); //todo
        executionReport.setStrategyOrderId(extraParams.get("LocalOrderNo"));
        executionReport.setSymbol(event.getSymbol());
        executionReport.setPrice(BigDecimal.valueOf(event.getPrice()));
        executionReport.setSide(event.getSide());
        executionReport.setOrdType(event.getType());
        executionReport.setDomesticType(extraParams.get("DomesticType"));
        executionReport.setOrderQty(BigDecimal.valueOf(event.getQuantity()));
        BigDecimal unit = basicParamCacheManager.getContractInfo(event.getSymbol()) == null ?
                new BigDecimal("1000") : basicParamCacheManager.getContractInfo(event.getSymbol()).getUnit();
        executionReport.setUnit(unit);
        executionReport.setLeavesQty(BigDecimal.valueOf(event.getLeaveQty()));
        executionReport.setCanceledQty(BigDecimal.valueOf(event.getMatchQty()));
        executionReport.setOrderQty(getExtraValue(extraParams, "38")); //todo 38 or quantity
        executionReport.setBusinessType(extraParams.get("BusinessType"));
        executionReport.setClientId(extraParams.get("ClientId"));
        executionReport.setCreditResult(extraParams.get("CreditResult")); //todo
        executionReport.setCurrency(extraParams.get("Currency"));
        executionReport.setApamaStatus(extraParams.get("ErrorID"));
        executionReport.setStatusMsg(extraParams.get("ErrorMsg"));

        executionReport.setOpenFlag(extraParams.get("EoFlag"));
        executionReport.setExchId(extraParams.get("Exchange"));
        executionReport.setCounterParty(extraParams.get("CounterParty"));
        executionReport.setForkPlate(extraParams.get("ForkPlate"));
        executionReport.setInventoryType(extraParams.get("InventoryType"));
        executionReport.setMemberId(extraParams.get("MemberId"));
        executionReport.setOrderAttr(extraParams.get("OrderAttr"));
        executionReport.setOrderDate(extraParams.get("OrderDate"));
        executionReport.setOrderTime(null == extraParams.get("OrderTimeStamp") ?
                null : LocalDateTime.parse(extraParams.get("OrderTimeStamp"), 
                extraParams.get("OrderTimeStamp").contains("-") ? TIME_FORMATTER : MATCH_TIME_FORMATTER));
        executionReport.setTagCode(extraParams.get("PositionTagCode"));
        executionReport.setTagName(extraParams.get("PositionTagName"));
        executionReport.setEoFlag(extraParams.get("EoFlag"));
        executionReport.setShFlag(extraParams.get("ShFlag"));
        executionReport.setStrategyId(extraParams.get("StrategyID"));
        executionReport.setInstanceId(extraParams.get("StrategyInstanceID"));

        executionReport.setOrdType(extraParams.get("Type"));
        executionReport.setUserName(extraParams.get("UserName"));
        executionReport.setSystemId(extraParams.get("SystemId"));
        executionReport.setTradePurpose(extraParams.get("TradePurpose"));
        executionReport.setTraderNo(extraParams.get("TraderNo"));
        executionReport.setVarietyId(extraParams.get("VarietyId"));
        executionReport.setExpiredTime(extraParams.get("ExpiredTime"));
        executionReport.setValidTime(extraParams.get("OrderTimeValidTime"));
        executionReport.setTick(extraParams.get("Tick"));
        executionReport.setAccuracy(extraParams.get("Accuracy"));
        executionReport.setIsHistoryContract(extraParams.get("IsHistoryContract"));
        executionReport.setDataSource(extraParams.get("dataSource"));
        executionReport.setType(extraParams.get("Type"));
        if (executionReport.getApamaStatus() != null 
                && (executionReport.getApamaStatus().equals("2") || executionReport.getApamaStatus().equals("3"))){
            executionReport.setExecId(extraParams.get("MatchNo"));
            //成交数据
            executionReport.setLastQty(BigDecimal.valueOf(event.getLastQty()));
            executionReport.setLastPrice(BigDecimal.valueOf(event.getLastPrice()));
            executionReport.setLastAmt(executionReport.getLastPrice().multiply(executionReport.getLastQty()).multiply(unit));
            
            // 在订单处理中，统一计算成交数据
            executionReport.setAvgPx(BigDecimal.valueOf(event.getAvgPrice()));
            executionReport.setCumQty(BigDecimal.valueOf(event.getCumQty())); //todo 待验证
            executionReport.setCumAmt(executionReport.getCumQty()
                    .multiply(executionReport.getAvgPx()).multiply(unit)); //todo 待验证
            executionReport.setMatchDate(extraParams.getOrDefault("MatchDate", ""));
            executionReport.setMatchTime(LocalDateTime.parse(extraParams.get("MatchTimeStamp"), MATCH_TIME_FORMATTER)
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
        return executionReport;
    }

    public PmOrderEntity toOrderEntity(ExecutionReport executionReport) {
        PmOrderEntity orderEntity = new PmOrderEntity();
        orderEntity.setOrderId(executionReport.getOrderId());
        if(StringUtils.hasText(executionReport.getMatchDate())
                && StringUtils.hasText(executionReport.getMatchTime())){
            orderEntity.setOrderDealTime(LocalDateTime.parse(executionReport.getMatchDate()
                    + " " + executionReport.getMatchTime(), MATCH_TIME_FORMATTER));
        }
        
        orderEntity.setExchOrderId(executionReport.getExchOrderId());
        orderEntity.setStrategyOrderId(executionReport.getStrategyOrderId());
        orderEntity.setSide(executionReport.getSide());
        orderEntity.setSymbol(executionReport.getSymbol());
        orderEntity.setCurrency(executionReport.getCurrency());
        orderEntity.setOrdType(executionReport.getOrdType());
        orderEntity.setOrderQty(executionReport.getOrderQty());
        orderEntity.setPrice(executionReport.getPrice());
        orderEntity.setAvgPx(executionReport.getAvgPx());
        orderEntity.setCumQty(executionReport.getCumQty());
        orderEntity.setCanceledQty(executionReport.getCanceledQty());
        orderEntity.setCumAmt(executionReport.getCumAmt());
        orderEntity.setUnit(executionReport.getUnit());
        orderEntity.setBusinessType(executionReport.getBusinessType());
        orderEntity.setStrategyId(executionReport.getStrategyId());
        orderEntity.setInstanceId(executionReport.getInstanceId());
        orderEntity.setUserName(executionReport.getUserName());
        orderEntity.setTagCode(executionReport.getTagCode());
        orderEntity.setTagName(executionReport.getTagName());
        orderEntity.setTimeInForce(executionReport.getTimeInForce());
        orderEntity.setExchId(executionReport.getExchId());
        orderEntity.setCounterParty(executionReport.getCounterParty());
        orderEntity.setMarketSegmentId(executionReport.getMarketSegmentId());
        orderEntity.setSecurityType(executionReport.getSecurityType());
        orderEntity.setMemberId(executionReport.getMemberId());
        orderEntity.setClientId(executionReport.getClientId());
        orderEntity.setInventoryType(executionReport.getInventoryType());
        orderEntity.setDomesticType(executionReport.getDomesticType());
        orderEntity.setTraderNo(executionReport.getTraderNo());
        orderEntity.setTradePurpose(executionReport.getTradePurpose());
        orderEntity.setOpenFlag(executionReport.getOpenFlag());
        orderEntity.setShFlag(executionReport.getShFlag());
        orderEntity.setOrderTime(null == executionReport.getOrderTime() ? 
                null : executionReport.getOrderTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        orderEntity.setOrderDate(executionReport.getOrderDate());
        orderEntity.setStatus(executionReport.getStatus());
        orderEntity.setStatusMsg(executionReport.getStatusMsg());
        orderEntity.setExtra(executionReport.getExtra());
        orderEntity.setCreateTime(LocalDateTime.now());
        orderEntity.setUpdateTime(LocalDateTime.now());
        return orderEntity;
    }

    public PmTradeEntity toTradeEntity(ExecutionReport executionReport) {
        PmTradeEntity tradeEntity = new PmTradeEntity();
        tradeEntity.setTraderNo(executionReport.getTraderNo());
        tradeEntity.setExecId(executionReport.getExecId());
        tradeEntity.setOrderId(executionReport.getOrderId());
        tradeEntity.setOrderQty(executionReport.getOrderQty());
        tradeEntity.setExchOrderId(executionReport.getLocalOrderNo());
        tradeEntity.setStrategyOrderId(executionReport.getStrategyOrderId());
        tradeEntity.setSide(executionReport.getSide());
        tradeEntity.setSymbol(executionReport.getSymbol());
        tradeEntity.setCurrency(executionReport.getCurrency());
        tradeEntity.setOrdType(executionReport.getOrdType());
        tradeEntity.setLastQty(executionReport.getLastQty());
        tradeEntity.setLastPx(executionReport.getLastPrice());
        tradeEntity.setLastAmt(executionReport.getLastAmt());
        tradeEntity.setBusinessType(executionReport.getBusinessType());
        tradeEntity.setStrategyId(executionReport.getStrategyId());
        tradeEntity.setInstanceId(executionReport.getInstanceId());
        tradeEntity.setUserName(executionReport.getUserName());
        tradeEntity.setTagCode(executionReport.getTagCode());
        tradeEntity.setTagName(executionReport.getTagName());
        tradeEntity.setCounterParty(executionReport.getCounterParty());
        tradeEntity.setExchId(executionReport.getExchId());
        tradeEntity.setSecurityType(executionReport.getSecurityType());
        tradeEntity.setMatchTime(executionReport.getMatchTime());
        tradeEntity.setMatchDate(executionReport.getMatchDate());
        tradeEntity.setMemberId(executionReport.getMemberId());
        tradeEntity.setClientId(executionReport.getClientId());
        tradeEntity.setInventoryType(executionReport.getInventoryType());
        return tradeEntity;
    }
    
    private String getExtraValue(Map<String, String> extraParams, String key) {
        if(extraParams != null && extraParams.containsKey(key)) {
            return extraParams.get(key);
        }
        return null;
    }
}
