package com.cmbc.oms.domain.order.ability.factory;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.quotes.cacheservice.MergeQuotesCacheService;
import com.cmbc.mds.forex.quotes.dto.PlxPrices;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.domain.order.ability.service.OrderAlgoService;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.position.model.entity.Positions;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import com.cmbc.oms.infrastructure.cache.PositionCacheManager;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import com.cmbc.oms.infrastructure.util.OrderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 19:25
 * @description
 */
@Slf4j
@Service
public class OrderFactory {
    
    @Autowired
    private OrderAlgoService algoService;
    
    @Autowired
    private BasicParamCacheManager basicParamCacheManager;
    
    @Autowired
    private MergeQuotesCacheService mergeQuotesCacheService;
    @Autowired
    private PositionCacheManager positionCacheManager;
    
    public List<NewOrder> createChildOrder(StrategyOrder strategyOrder) {
        List<NewOrder> newOrderList;
        // 1. 判断是否走 VWAP (境外)
        if (BaseConstants.DOMESTIC_TYPE_OUTER.equals(strategyOrder.getDomesticType())) {
            // VWAP 逻辑
            newOrderList = algoService.makeVwapOrder(strategyOrder, 
                    getPloyPrice(strategyOrder.getSymbol(), strategyOrder.getExchCode(), strategyOrder.getCounterParty()));
            
        } else {
            // 2. 单笔报价下单逻辑
            newOrderList = createDomesticOrders(strategyOrder);
        }
        
        if (newOrderList == null || newOrderList.isEmpty()) {
            log.warn("[{}] NewOrderList is null", strategyOrder.getOrderId());
        }else{
            strategyOrder.setTotalChildCount(newOrderList.size());
        }
        strategyOrder.setNewOrderList(newOrderList);
        return newOrderList;
    }
    
    /**
     * [细化] 简单报价下单逻辑
     * 流程: 获取行情 -> 计算价格(加点) -> 计算数量 -> 组装对象 -> 发单
     */
    private List<NewOrder> createDomesticOrders(StrategyOrder strategyOrder) {
        ContractInfoBasic contractInfo = basicParamCacheManager.getContractInfo(strategyOrder.getSymbol());
        String posSide = strategyOrder.getSide().equals(BusinessConstant.BUY_SIDE) ? 
                BusinessConstant.SELL_SIDE : BusinessConstant.BUY_SIDE;
        Positions position = 
                positionCacheManager.getTotalPosition(strategyOrder.getMemberId()+strategyOrder.getSymbol(), posSide);
        BigDecimal offsetQty;
        BigDecimal offsetLastQty;
        BigDecimal offsetTodayQty;
        log.info("获取头寸信息:{}", position);
        if(position == null){
            log.warn("[{}] Position is null", strategyOrder.getOrderId());
            offsetQty = BigDecimal.ZERO;
            offsetLastQty = BigDecimal.ZERO;
            offsetTodayQty = BigDecimal.ZERO;
        }else {
            offsetQty = position.getOffsetQty().setScale(0, RoundingMode.DOWN);   //可平量
            offsetLastQty = position.getOffsetLastQty().setScale(0, RoundingMode.DOWN);  //可平昨
            offsetTodayQty = position.getOffsetTodayQty().setScale(0, RoundingMode.DOWN); //可平今
        }
        
        List<NewOrder> newOrderList = new ArrayList<>();
        if (contractInfo == null) {
            log.warn("[{}] ContractInfo is null", strategyOrder.getOrderId());
            return null;
        }
        
        String inventoryType = contractInfo.getInventoryType();
        String eoFlag = "";
        BigDecimal leftQty = strategyOrder.getQty();
        
        if (BusinessConstant.SPOT.equals(inventoryType)) {
            NewOrder newOrder = buildNewOrder(eoFlag, strategyOrder.getQty(), strategyOrder, contractInfo);
            newOrderList.add(newOrder);
        }else {
            if(offsetQty.compareTo(BigDecimal.ZERO) > 0 && "1".equals(strategyOrder.getOffsetFlag())){   //先平后开处理
                if(contractInfo.getExchCode().equals(BusinessConstant.SH_FUTURES_EXCHANGE)){  //上期所处理
                    BigDecimal newOrderQty = BigDecimal.ZERO;
                    if(offsetTodayQty.compareTo(BigDecimal.ZERO) > 0 && leftQty.compareTo(BigDecimal.ZERO) > 0){
                        newOrderQty = leftQty.min(offsetTodayQty);
                        /**先平今*/
                        eoFlag = BusinessConstant.FLAT_TODAY_POSITION;
                        NewOrder newOrder = buildNewOrder(eoFlag, newOrderQty, strategyOrder, contractInfo);
                        newOrderList.add(newOrder);
                        offsetQty = offsetQty.subtract(newOrderQty);
                        leftQty = leftQty.subtract(newOrderQty);
                        offsetTodayQty = offsetTodayQty.subtract(newOrderQty);
                    }
                    
                    if(leftQty.compareTo(BigDecimal.ZERO) > 0 && offsetLastQty.compareTo(BigDecimal.ZERO) > 0){
                        newOrderQty = leftQty.min(offsetLastQty);
                        eoFlag = BusinessConstant.FLAT_POSITION;
                        NewOrder newOrder = buildNewOrder(eoFlag, newOrderQty, strategyOrder, contractInfo);
                        newOrderList.add(newOrder);
                        offsetQty = offsetQty.subtract(newOrderQty);
                        leftQty = leftQty.subtract(newOrderQty);
                        offsetLastQty = offsetLastQty.subtract(newOrderQty);
                    }
                }else {
                    BigDecimal newOrderQty = leftQty.min(offsetQty);
                    if(newOrderQty.compareTo(BigDecimal.ZERO) > 0){
                        eoFlag = BusinessConstant.FLAT_POSITION;
                        NewOrder newOrder = buildNewOrder(eoFlag, newOrderQty, strategyOrder, contractInfo);
                        newOrderList.add(newOrder);
                        offsetQty = offsetQty.subtract(newOrderQty);
                        leftQty = leftQty.subtract(newOrderQty);
                    }
                }
            }
            if(leftQty.compareTo(BigDecimal.ZERO)>0){
                eoFlag = BusinessConstant.OPEN_POSITION;
                NewOrder newOrder = buildNewOrder(eoFlag, leftQty, strategyOrder, contractInfo);
                newOrderList.add(newOrder);
                leftQty = BigDecimal.ZERO;
            }
        }
        
        // 设置扩展参数(对应 Apama extParams)
        return newOrderList;
    }
    
    private NewOrder buildNewOrder(String eoFlag, BigDecimal orderQty, StrategyOrder strategyOrder, ContractInfoBasic contractInfo){
        // 组装订单对象
        NewOrder order = new NewOrder();
        // 构建SDK订单对象
        //order.setOrderId(String.valueOf(System.currentTimeMillis()));
        order.setOrderId(OrderUtil.generateChildOrderId());
        order.setParentOrderId(strategyOrder.getOrderId());
        order.setStrategyInstanceID(strategyOrder.getInstanceId());
        order.setSymbol(strategyOrder.getSymbol());
        order.setLocalOrderNo(order.getOrderId());
        order.setSide(strategyOrder.getSide());
        order.setPrice(strategyOrder.getPrice());
        order.setExpiredTime(strategyOrder.getTimeOut());
        order.setType("Limit");        // 目前境内仅支持Limit
        order.setBusinessType(strategyOrder.getBusinessType());
        order.setExchCode("DIMPLE");
        order.setUserName(strategyOrder.getUserId());
        order.setNetPosition(strategyOrder.getNetPosition()); // 添加净敞口头寸
        order.setUnit();
        order.setOrderQty(orderQty);
        order.setOrderTime(LocalDateTime.now()); // 添加订单委托时间
        order.setLeavesQty(orderQty); // 默认为下单数量
        order.setExchange(strategyOrder.getExchCode());
        order.setPositionTagCode(strategyOrder.getTagCode());
        order.setPositionTagName(strategyOrder.getTagName());
        order.setMemberId(strategyOrder.getMemberId());
        order.setClientId(strategyOrder.getClientId());
        order.setDomesticType(strategyOrder.getDomesticType());
        order.setOwnerId(strategyOrder.getUserId());
        order.setCurrency(strategyOrder.getCurrency());
        order.setExchCode(contractInfo.getExchCode());
        order.setTradePurpose("TL"); //todo
        order.setInventoryType(contractInfo.getInventoryType());
        order.setStepPosition(0);  //todo
        order.setAccuracy(contractInfo.getAccuracy());
        order.setVarietyId(contractInfo.getVarietyId());
        order.setTraderNo(strategyOrder.getTraderNo());
        order.setEoFlag(eoFlag);
        order.setOpeningClosingType(strategyOrder.getOffsetFlag());//todo 配置
        order.setShFlag("1");//todo
        log.info("[{}] build newOrder.{}", strategyOrder.getInstanceId(), order);
        return order;
    }
    
    public PlxPrices getPloyPrice(String symbol, String exchId, String counterparties) {
        // 1. 解析对手方列表
        List<String> providers = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        if ((counterparties == null || counterparties.trim().isEmpty()) || (exchId == null || exchId.trim().isEmpty())) {
            return null;
        } else {
            // 关键：对冲拆分，并去除每一项的空格
            providers = Arrays.asList(counterparties.split("#"));
            sources = Arrays.asList(exchId.split("#"));
        }
        return mergeQuotesCacheService.getPloyPrices(sources, providers, symbol);
    }
}
