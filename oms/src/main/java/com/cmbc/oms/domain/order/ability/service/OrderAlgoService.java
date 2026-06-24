package com.cmbc.oms.domain.order.ability.service;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.quotes.dto.ProviderInfo;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import com.cmbc.oms.infrastructure.util.OrderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OrderAlgoService {

    /**
     * VWAP 拆单核心算法
     * 对应 Apama: baseOrderUtil.makeAbroadVwapNewOrder
     */
    public List<NewOrder> makeVwapOrder(StrategyOrder strategyOrder, PloyPrices ployPrices) {
        
        List<NewOrder> childOrders = new ArrayList<>();
        
        if (ployPrices == null) {
            log.warn("[{}] VWAP拆单失败：聚合行情为空", strategyOrder.getOrderId());
            return null;
        }
        
        // 1. 根据方向选择对手盘数据
        // 买入平空 -> 吃卖盘 (Ask), 卖出平多 -> 吃买盘 (Bid)
        Map<BigDecimal, Map<String, ProviderInfo>> marketDepth;
        boolean isReverseTraversal;
        // 2. 确定遍历顺序 (Set<BigDecimal> prices)
        List<BigDecimal> sortedPrices;
        if (strategyOrder.getSide() == BusinessConstant.SELL_SIDE) {
            // 卖出: 吃Bid, Bid最好价格是最高的
            marketDepth = ployPrices.getFdBid();
            sortedPrices = ployPrices.getSortedBidPrices();
            isReverseTraversal = true;
        } else {
            // 买入: 吃Ask, Ask最好价格是最底的
            marketDepth = ployPrices.getFdAsk();
            sortedPrices = ployPrices.getSortedAskPrices();
            isReverseTraversal = false;
        }
        log.info("VWAP拆单: 获取行情: {}", JSONObject.toJSONString(marketDepth));
        if(marketDepth == null || marketDepth.isEmpty()) {
            log.warn("[{}] VWAP拆单失败: 聚合行情为空", strategyOrder.getOrderId());
            return null;
        }
        
        log.info("VWAP拆单: 价格列表: {}", JSONObject.toJSONString(sortedPrices));
        
        BigDecimal remainingQty = strategyOrder.getQty();
        BigDecimal targetQty = strategyOrder.getQty();
        // 3. 深度遍历 (价格 -> 报价商 -> 数量层级)
        // 对应 Apama loop: while(priceIdx >= 0)
        for (BigDecimal levelPrice : sortedPrices) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
            
            // 获取该价格档位下的所有报价商
            Map<String, ProviderInfo> providers = marketDepth.get(levelPrice);
            if (providers == null) continue;
            
            // 遍历报价商
            for (Map.Entry<String, ProviderInfo> entry : providers.entrySet()) {
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                
                String providerName = entry.getKey();
                ProviderInfo info = entry.getValue();
                
                // 遍历该报价商的数量列表
                List<BigDecimal> quantities = info.getQuantity();
                int keyNum = 0; // 用于寻找quoteID的下标
                log.info("VWAP拆单:价格: {}, 服务商: {}, 数量: {}", levelPrice, providerName, JSONObject.toJSONString(quantities));
                for (BigDecimal providerQty : quantities) {
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                    
                    // 过滤无效数量
                    if (providerQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    
                    // 计算本次拆单数量: 取 剩余量 和 报价量 的较小值
                    BigDecimal orderQty = remainingQty.min(providerQty);
                    
                    String quoteEntryId = info.getQuoteId();
                    // --- 核心: 特殊服务商校验 (FXALL, GS等) ---
                    String exchId = info.getSource();
                    String counterParty = info.getProvider();
                    String orderType = BusinessConstant.LIMIT_ORDER;
                    if (BusinessConstant.FXALL_SERVICE_NAME.equals(exchId) || BusinessConstant.GS_SERVICE_NAME.equals(exchId)) {
                        if (StringUtils.isEmpty(quoteEntryId)) {
                            log.error("[{}] 拆单异常: 未找到QuoteID, 服务商: {}", strategyOrder.getOrderId(), counterParty);
                            //todo 通知管理器
                            continue; //跳过此单, 寻找下一个
                        }
                        orderType = BusinessConstant.QUOTED_ORDER;
                    }
                    
                    // 4. 生成子单
                    NewOrder childOrder = buildAboradOrder(strategyOrder, 
                            levelPrice, orderQty, exchId, counterParty, quoteEntryId, orderType);
                    log.info("[{}] VWAP拆单生成子单: {}", strategyOrder.getOrderId(), childOrder);
                    childOrders.add(childOrder);
                    
                    // 更新剩余量
                    remainingQty = remainingQty.subtract(orderQty);
                }
            }
        }
        log.info("[{}] VWAP拆单完成, 总目标: {}, 实际拆单: {} 笔, 剩余未平: {}", 
                strategyOrder.getInstanceId(), targetQty, childOrders.size(), remainingQty);
        
        return childOrders;
    }
    
    private NewOrder buildAboradOrder(StrategyOrder strategyOrder, BigDecimal price, BigDecimal qty, 
            String exchId, String counterParty, String quoteEntryId, String orderType) {
        NewOrder order = new NewOrder();
        // 构建订单对象
        order.setOrderId(OrderUtil.generateChildOrderId());
        order.setParentOrderId(strategyOrder.getOrderId());
        order.setStrategyInstanceID(strategyOrder.getInstanceId());
        order.setSymbol(strategyOrder.getSymbol());
        order.setLocalOrderNo(order.getOrderId());
        order.setSide(strategyOrder.getSide());
        order.setPrice(price);
        order.setType(orderType);
        order.setNetPosition(strategyOrder.getNetPosition()); // 添加净敞口
        order.setOrderTime(LocalDateTime.now()); // 添加订单委托时间
        order.setQuoteEntryId(quoteEntryId);
        order.setBusinessType(strategyOrder.getBusinessType());
        order.setDataSource(exchId + "-FIX");
        order.setExchCode(exchId + "-FIX");
        order.setCounterParty(counterParty);
        order.setUserName(strategyOrder.getUserName());
        order.setUnit(1); //境外单位为1
        order.setOrderQty(qty);
        order.setLeavesQty(qty);
        order.setExchCode(exchId);
        order.setPositionTagCode(strategyOrder.getTagCode());
        order.setPositionTagName(strategyOrder.getTagName());
        order.setDomesticType(strategyOrder.getDomesticType());
        order.setExpiredTime(BigDecimal.ZERO); //境外无到期时间
        order.setTraderNo(strategyOrder.getTraderNo());
        order.setCurrency(strategyOrder.getCurrency());
        //以下数据准确是否使用
        order.setVarietyId("au");
        order.setStepPosition(0);
        order.setAccuracy(0);
        order.setShFlag("1");
        order.setTradePurpose("TL");
        order.setStrategyID(strategyOrder.getInstanceId());
        order.setInventoryType("0");
        order.setOwnerId(strategyOrder.getUserId());
        order.setCurrency("XAU"); //todo 境外默认XAU
        
        return order;
    }
}
