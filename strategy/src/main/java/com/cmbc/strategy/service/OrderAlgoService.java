package com.cmbc.strategy.service;

import com.cmbc.strategy.constant.Side;
import com.cmbc.strategy.domain.model.order.NewOrder;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.market.PriceProviderInfo;
import com.cmbc.strategy.domain.model.order.OrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class OrderAlgoService {

    /**
     * VWAP 拆单核心算法
     * 对应 Apama: BaseOrderUtil.makeAbroadVwapNewOrder
     */
    public List<NewOrder> makeVwapOrder(
            Side side,
            BigDecimal qty,
            String strategyId,
            HedgeStrategyConfig config,
            SymbolTimeSlice symbolSlice,
            PloyPrices ployPrices) {

        List<NewOrder> childOrders = new ArrayList<>();

        // 1. 根据方向选择对手盘数据
        // 买入平空 -> 吃卖盘 (Ask)；卖出平多 -> 吃买盘 (Bid)
        Map<BigDecimal, Map<String, PriceProviderInfo>> marketDepth;
//        boolean isReverseTraversal;

        if (side == Side.SELL) {
            // 卖出：吃Bid。Bid最好价格是最高的
            marketDepth = ployPrices.getFdBid();
//            isReverseTraversal = true;
        } else {
            // 买入：吃Ask。Ask最好价格是最低的
            marketDepth = ployPrices.getFdAsk();
//            isReverseTraversal = false;
        }

        if (marketDepth == null || marketDepth.isEmpty()) {
            log.warn("[{}] VWAP拆单失败: 聚合行情为空", strategyId);
            return null;
        }

        // 2. 确定遍历顺序 (Set<BigDecimal> prices)        todo 先默认价格有序
        Set<BigDecimal> priceLevels = marketDepth.keySet();

        BigDecimal remainingQty = qty;
        BigDecimal targetQty = qty;
        // 3. 深度遍历 (价格 -> 报价商 -> 数量层级)
        // 对应 Apama loop: while(priceIdx >= 0)
        for (BigDecimal levelPrice : priceLevels) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;

            // 获取该价格档位下的所有报价商
            Map<String, PriceProviderInfo> providers = marketDepth.get(levelPrice);
            if (providers == null) continue;

            // 遍历报价商
            for (Map.Entry<String, PriceProviderInfo> entry : providers.entrySet()) {
                if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;

                String providerName = entry.getKey();
                PriceProviderInfo info = entry.getValue();

                // 遍历该报价商的数量列表
                List<BigDecimal> quantities = info.getQuantity();
                int keyNum = 0; // 用于寻找QuoteID的下标

                for (BigDecimal providerQty : quantities) {
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;

                    // 过滤无效数量
                    if (providerQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                    // 计算本次拆单数量：取 剩余量 和 报价量 的较小值
                    BigDecimal orderQty = remainingQty.min(providerQty);

                    // --- 核心：获取 QuoteEntryID ---
                    // 逻辑参考 Apama: quoteKey := quoteKey + keyNum.toString()
                    String quoteKey = QUOTE_ENTRY_ID_KEY;
                    if (keyNum > 0) {
                        quoteKey = quoteKey + keyNum;
                    }
                    String quoteEntryId = info.getPriceAttributes().get(quoteKey);

                    // --- 核心：特殊服务商校验 (FXALL, GS等) ---
                    // 逻辑参考 Apama
                    String serviceUpper = providerName.toUpperCase();
                    if (quoteEntryId == null || quoteEntryId.isEmpty()) {
                        log.error("[{}] 拆单忽略: 未找到QuoteID, 服务商: {}, 数量: {}", strategyId, providerName, orderQty);
                        keyNum++;
                        continue; // 跳过此单，寻找下一个
                    }


                    // 4. 生成子单
                    NewOrder childOrder = buildChildOrder(
                            symbolSlice.getSymbol(), side, levelPrice, orderQty,
                            providerName, quoteEntryId, strategyId
                    );
                    childOrders.add(childOrder);

                    // 更新剩余量
                    remainingQty = remainingQty.subtract(orderQty);

                    // 增加下标计数 (对应 Apama keyNum := keyNum + 1)
                    keyNum++;
                }
            }
        }

        // 记录日志
        log.info("[{}] VWAP拆单完成. 总目标: {}, 实际拆单: {} 笔, 剩余未平: {}",
                strategyId, targetQty, childOrders.size(), remainingQty);

        return childOrders;
    }

    private NewOrder buildChildOrder(String symbol, Side side, BigDecimal price, BigDecimal qty,
                                         String providerName, String quoteEntryId, String strategyId) {
        // 构建SDK订单对象
        OrderRequest.OrderRequestBuilder builder = OrderRequest.builder()
                .symbol(symbol)
                .side(side)
                .type(OrderType.LIMIT) // 通常VWAP底层也是发Limit单
                .price(price)
                .quantity(qty)
                .timeInForce(TimeInForce.GTC);

        // 设置扩展参数 (对应 Apama extParams)
        Map<String, String> extras = new HashMap<>();
        extras.put("COUNTER_PARTY", providerName); // 交易对手
        extras.put("DATA_SOURCE", providerName);   // 数据源
        extras.put("OMS_SEND_SERVICE_ID", providerName);

        if (quoteEntryId != null) {
            extras.put("QUOTE_ENTRY_ID", quoteEntryId); // 关键字段
        }

        // 针对德商/FXALL可能需要设为 QUOTED_ORDER 类型
        if (providerName.toUpperCase().contains(FXALL_SERVICE_NAME)) {
            extras.put("ORDER_TYPE_NAME", "QUOTED");
        }

        builder.extraParams(extras);
        return builder.build();
    }

}
