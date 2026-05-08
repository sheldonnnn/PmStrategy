package com.cmbc.mds.distribution;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class PloyPrices {

    private String symbol;
    List<String> sources;
    List<String> providers;
    String strategyId;
    String traderId;
    /**
     * bid 多档价格聚合集合
     * 外层 Map Key: BigDecimal 价格档位 (Price)
     * 内层 Map Key: String 提供商ID或标识 (ProviderId)
     * 内层 Map Value: ProviderInfo 该提供商在该价格下的原始报价明细 (包含报价量等信息)
     */
    private Map<BigDecimal, Map<String, ProviderInfo>> fdBid;

    /**
     * ask 多档价格聚合集合
     * 外层 Map Key: BigDecimal 价格档位 (Price)
     * 内层 Map Key: String 提供商ID或标识 (ProviderId)
     * 内层 Map Value: ProviderInfo 该提供商在该价格下的原始报价明细 (包含报价量等信息)
     */
    private Map<BigDecimal, Map<String, ProviderInfo>> fdAsk;

    // --- 扩展信息 ---
    private Map<String, String> extrams; // key介绍: depthTime:行情时间 riseLimit:涨停板价格 fallLimit:跌停板价格 lastPrice:最新价

    /**
     * 获取最佳买入价 (Best Bid)
     * 逻辑：买方愿意出的最高价
     */
    public BigDecimal getBestBidPx() { // 买1
        // 1. 判定空 NPE 异常
        if (fdBid == null || fdBid.isEmpty()) {
            return null;
        }
        // 2. 优化计算：无需全量排序，直接获取集合中的最大值，时间复杂度从 O(n log n) 降至 O(n)
        return Collections.max(fdBid.keySet());
    }

    /**
     * 获取最佳卖出价 (Best Ask)
     * 逻辑：卖方愿意接受的最低价
     */
    public BigDecimal getBestAskPx() {
        if (fdAsk == null || fdAsk.isEmpty()) {
            return null;
        }
        return Collections.min(fdAsk.keySet());
    }

    @JsonIgnore
    public BigDecimal getSecondBestBidPx() { // 买2
        // 1. 判空及元素数量校验：如果档位少于2个，则不存在“第二优价”，防止 IndexOutOfBoundsException
        if (fdBid == null || fdBid.size() < 2) {
            return null;
        }

        // 2. 提取价格并排序
        List<BigDecimal> prices = new ArrayList<>(fdBid.keySet());
        Collections.sort(prices);

        // 3. 修复原代码逻辑 Bug：买盘最高价为最优（最后一个元素），次优价应为倒数第二个元素
        return prices.get(prices.size() - 2);
    }

    /**
     * 获取第二佳卖出价 (Second Best Ask)
     * 逻辑：卖方愿意接受的第二低价
     */
    public BigDecimal getSecondBestAskPx() { // 卖2
        // 1. 判空及元素数量校验：防止档位不足报错
        if (fdAsk == null || fdAsk.size() < 2) {
            return null;
        }

        // 2. 提取价格并排序
        List<BigDecimal> prices = new ArrayList<>(fdAsk.keySet());
        Collections.sort(prices);

        // 3. 卖盘最低价为最优（第0个元素），次优价为正数第二个元素（索引为 1）
        return prices.get(1);
    }

    /**
     * 获取中间价 (Mid Price)
     * 计算公式: (Best Ask + Best Bid) / 2
     */
    public BigDecimal getMidPx() {
        BigDecimal bestBidPx = getBestBidPx();
        BigDecimal bestAskPx = getBestAskPx();
        if (bestBidPx == null || bestAskPx == null) {
            return null;
        }
        return bestBidPx.add(bestAskPx).divide(new BigDecimal(2), 8, RoundingMode.HALF_UP);
    }


}
