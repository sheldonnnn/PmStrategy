package com.cmbc.strategy.domain.model.market;

import lombok.Data;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class PloyPrices {

    // Key: 价格, Value: Map<报价商名称, 详情>
    // 对应 Apama: dictionary<float,dictionary<string,ProviderInfo> >
    private Map<BigDecimal, Map<String, PriceProviderInfo>> fdBid = new ConcurrentHashMap<>();
    private Map<BigDecimal, Map<String, PriceProviderInfo>> fdAsk = new ConcurrentHashMap<>();

    public BigDecimal getBestBidPx(){
//        return fdBid.lastKey();
        if (fdBid.isEmpty()) return null;

        // 1. 提取所有价格 Key
        List<BigDecimal> prices = new ArrayList<>(fdBid.keySet());

        // 2. 排序 (O(n log n))
        Collections.sort(prices);

        // 3. 买盘最高价为最优价 (最后一个元素)
        return prices.get(prices.size() - 1);
    }

    public BigDecimal getBestAskPx(){
//        return fdAsk.firstKey();
        if (fdAsk.isEmpty()) return null;

        // 1. 提取所有价格 Key
        List<BigDecimal> prices = new ArrayList<>(fdBid.keySet());

        // 2. 排序 (O(n log n))
        Collections.sort(prices);

        // 3. 买盘最高价为最优价 (最后一个元素)
        return prices.get(0);
    }

    public BigDecimal getSecondBestBidPx(){
//        return fdBid.lastKey();
        if (fdBid.isEmpty()) return null;

        // 1. 提取所有价格 Key
        List<BigDecimal> prices = new ArrayList<>(fdBid.keySet());

        // 2. 排序 (O(n log n))
        Collections.sort(prices);

        // 3. 买盘最高价为最优价 (最后一个元素)
        return prices.get(prices.size() - 2);
    }
    public BigDecimal getSecondBestAskPx(){
//        return fdAsk.firstKey();
        if (fdAsk.isEmpty()) return null;

        // 1. 提取所有价格 Key
        List<BigDecimal> prices = new ArrayList<>(fdBid.keySet());

        // 2. 排序 (O(n log n))
        Collections.sort(prices);

        // 3. 买盘最高价为最优价 (最后一个元素)
        return prices.get(1);
    }

    public BigDecimal getMidPx(){
        return getBestAskPx().add(getBestBidPx()).divide(new BigDecimal(2), 2, BigDecimal.ROUND_HALF_UP);
    }

}
