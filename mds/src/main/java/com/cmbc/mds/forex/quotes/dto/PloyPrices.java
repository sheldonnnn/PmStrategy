package com.cmbc.mds.forex.quotes.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cmbc.mds.forex.common.utils.DateAndTimeUtils;

/**
 * 聚合价格实体类
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PloyPrices {

    // --- 基础信息字段 ---
    String symbol; // 货币对
    List<String> sources; // 价格源
    List<String> providers; // 价格提供商

    // --- 基础价格字段 (BigDecimal) ---
    private BigDecimal bidOptimalPrice; // 买方最优价
    private BigDecimal bidWorstPrice; // 买方最差价
    private BigDecimal askOptimalPrice; // 卖方最优价
    private BigDecimal askWorstPrice; // 卖方最差价

//    // --- VWAP 价格字段 (BigDecimal) ---
//    private BigDecimal vwapBidOptimalPrice; // 买方vwap最优价
//    private BigDecimal vwapBidWorstPrice; // 买方vwap最差价
//    private BigDecimal vwapBidSecondBestPrice; // 买方vwap次优价
//    private BigDecimal vwapAskOptimalPrice; // 卖方vwap最优价
//    private BigDecimal vwapAskWorstPrice; // 卖方vwap最差价
//    private BigDecimal vwapAskSecondBestPrice; // 卖方vwap次优价
//    private Integer quantity; // vwap计算参数数量，默认为聚合后的最优价量

//    // --- 价量分布 (Map + List) ---
//    private Map<String, List<BigDecimal>> bidOptimalQty; // 买方最优价量: Map<Provider, List<Volume>>
//    private Map<String, List<BigDecimal>> bidWorstQty; // 买方最差价量
//    private Map<String, List<BigDecimal>> askOptimalQty; // 卖方最优价量
//    private Map<String, List<BigDecimal>> askWorstQty; // 卖方最差价量

    // --- 聚合深度集合 (Map嵌套) ---
    /**
     * bid多档价格聚合集合
     * 外层 Map Key: BigDecimal 价格档位 (Price)
     * 内层 Map Key: String 提供商ID或标识 (ProviderId)
     * 内层 Map Value: ProviderInfo 该提供商在该价格下的原始报价明细 (包含报价量等信息)
     */
    private Map<BigDecimal, Map<String, ProviderInfo>> fdBid;

    /**
     * ask多档价格聚合集合
     * 外层 Map Key: BigDecimal 价格档位 (Price)
     * 内层 Map Key: String 提供商ID或标识 (ProviderId)
     * 内层 Map Value: ProviderInfo 该提供商在该价格下的原始报价明细 (包含报价量等信息)
     */
    private Map<BigDecimal, Map<String, ProviderInfo>> fdAsk;

    // --- 扩展信息 ---
    private Map<String, String> extrams; // key介绍：depthTime:行情时间 riseLimit:涨停板价格 fallLimit:跌停板价格 lastPrice:最新价

    private long updateTime; // 记录最后更新时间的时间戳

    /**
     * 获取格式化后的更新时间
     */
    public String getFormattedUpdateTime() {
        if (updateTime == 0) {
            return null;
        }
        return java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(updateTime), ZoneId.systemDefault())
                .format(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND);
    }

    /**
     * 获取最佳买入价 (Best Bid)
     * 逻辑：买方愿意出的最高价
     */
    @JsonIgnore
    public BigDecimal getBestBidPx() {
        // 1. 判空防 NPE 异常
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
    @JsonIgnore
    public BigDecimal getBestAskPx() {
        // 1. 判空防 NPE 异常
        if (fdAsk == null || fdAsk.isEmpty()) {
            return null;
        }
        // 2. 优化计算：直接获取集合中的最小值，提升性能
        return Collections.min(fdAsk.keySet());
    }

    /**
     * 获取第二佳买入价 (Second Best Bid)
     * 逻辑：买方愿意出的第二高价
     */
    @JsonIgnore
    public BigDecimal getSecondBestBidPx() {
        // 1. 判空及元素数量校验：如果档位少于2个，则不存在"第二优价"，防止 IndexOutOfBoundsException
        if (fdBid == null || fdBid.size() < 2) {
            return null;
        }

        // 2. 单次扫描获取第一、第二高价，避免复制列表和排序
        BigDecimal best = null;
        BigDecimal second = null;
        for (BigDecimal price : fdBid.keySet()) {
            if (best == null || price.compareTo(best) > 0) {
                second = best;
                best = price;
            } else if (second == null || price.compareTo(second) > 0) {
                second = price;
            }
        }

        // 3. 买盘最高价为最优，次高价为第二优
        return second;
    }

    /**
     * 获取第二佳卖出价 (Second Best Ask)
     * 逻辑：卖方愿意接受的第二低价
     */
    @JsonIgnore
    public BigDecimal getSecondBestAskPx() {
        // 1. 判空及元素数量校验：防止档位不足报错
        if (fdAsk == null || fdAsk.size() < 2) {
            return null;
        }

        // 2. 单次扫描获取第一、第二低价，避免复制列表和排序
        BigDecimal best = null;
        BigDecimal second = null;
        for (BigDecimal price : fdAsk.keySet()) {
            if (best == null || price.compareTo(best) < 0) {
                second = best;
                best = price;
            } else if (second == null || price.compareTo(second) < 0) {
                second = price;
            }
        }

        // 3. 卖盘最低价为最优，次低价为第二优
        return second;
    }

    /**
     * 获取中间价 (Mid Price)
     * 计算公式: (Best Ask + Best Bid) / 2
     */
    @JsonIgnore
    public BigDecimal getMidPx() {
        BigDecimal bestBidPx = getBestBidPx();
        BigDecimal bestAskPx = getBestAskPx();

        // 1. 判空逻辑补全：买卖最优价必须同时存在才能计算中间价
        if (bestBidPx == null || bestAskPx == null) {
            return null;
        }

        // 2. 计算与精度处理：
        // 优化点1：使用 BigDecimal.valueOf(2) 代替 new BigDecimal(2) 节省内存开销
        // 优化点2：使用 RoundingMode.HALF_UP 替代已废弃的 BigDecimal.ROUND_HALF_UP
        // 优化点3：外汇场景 (Forex) 通常保留4到5位小数，这里暂时设为4位小数（原代码为2位，可根据您的实际业务精度要求调整）
        return bestAskPx.add(bestBidPx).divide(BigDecimal.valueOf(2), 5, RoundingMode.HALF_UP);
    }

    /**
     * 获取按序排列的买方价格列表 (从优到劣：降序)
     */
    @JsonIgnore
    public List<BigDecimal> getSortedBidPrices() {
        if (fdBid == null || fdBid.isEmpty()) {
            return new ArrayList<>();
        }
        List<BigDecimal> prices = new ArrayList<>(fdBid.keySet());
        prices.sort(Collections.reverseOrder());
        return prices;
    }

    /**
     * 获取按序排列的卖方价格列表 (从优到劣：升序)
     */
    @JsonIgnore
    public List<BigDecimal> getSortedAskPrices() {
        if (fdAsk == null || fdAsk.isEmpty()) {
            return new ArrayList<>();
        }
        List<BigDecimal> prices = new ArrayList<>(fdAsk.keySet());
        Collections.sort(prices);
        return prices;
    }

    /**
     * 计算特定价格档位的汇总数量
     * 遍历所有 ProviderInfo 中的 quantity 列表，将所有数值进行累加
     */
    private BigDecimal calculateTotalVolume(Map<String, ProviderInfo> providerMap) {
        if (providerMap == null || providerMap.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ProviderInfo info : providerMap.values()) {
            if (info != null && info.getQuantity() != null) {
                for (BigDecimal qty : info.getQuantity()) {
                    if (qty != null) {
                        total = total.add(qty);
                    }
                }
            }
        }
        return total;
    }

    /**
     * 根据价格和档位数据组装 PriceVolumeInfo
     */
    private PriceVolumeInfo buildVolumeInfo(BigDecimal price, Map<BigDecimal, Map<String, ProviderInfo>> depthMap) {
        if (price == null || depthMap == null || !depthMap.containsKey(price)) {
            return null;
        }
        Map<String, ProviderInfo> providerMap = depthMap.get(price);
        BigDecimal totalVolume = calculateTotalVolume(providerMap);
        List<ProviderInfo> providerDataList = providerMap == null ? new ArrayList<>() : new ArrayList<>(providerMap.values());
        
        return PriceVolumeInfo.builder()
                .price(price)
                .totalVolume(totalVolume)
                .providerData(providerDataList)
                .build();
    }

    /**
     * 获取当前最优档的买方价量信息
     */
    public PriceVolumeInfo getBestBidVolumeInfo() {
        return buildVolumeInfo(getBestBidPx(), fdBid);
    }

    /**
     * 获取当前次优买价档位的价格和汇总数量。
     */
    public PriceVolumeInfo getSecondBestBidVolumeInfo() {
        return buildVolumeInfo(getSecondBestBidPx(), fdBid);
    }

    /**
     * 获取当前最优档的卖方价量信息
     */
    public PriceVolumeInfo getBestAskVolumeInfo() {
        return buildVolumeInfo(getBestAskPx(), fdAsk);
    }

    /**
     * 获取当前次优卖价档位的价格和汇总数量。
     */
    public PriceVolumeInfo getSecondBestAskVolumeInfo() {
        return buildVolumeInfo(getSecondBestAskPx(), fdAsk);
    }

    /**
     * 根据指定价格查询对应的买方报价量信息
     */
    public PriceVolumeInfo getBidVolumeInfo(BigDecimal price) {
        return buildVolumeInfo(price, fdBid);
    }

    /**
     * 根据指定价格查询对应的卖方报价量信息
     */
    public PriceVolumeInfo getAskVolumeInfo(BigDecimal price) {
        return buildVolumeInfo(price, fdAsk);
    }

    /**
     * 获取当前所有挡位的买方价量列表 (根据价格从优到劣：降序)
     */
    public List<PriceVolumeInfo> getAllBidVolumeInfos() {
        List<BigDecimal> sortedPrices = getSortedBidPrices();
        List<PriceVolumeInfo> result = new ArrayList<>();
        for (BigDecimal price : sortedPrices) {
            PriceVolumeInfo info = buildVolumeInfo(price, fdBid);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 获取当前所有挡位的卖方价量列表 (根据价格从优到劣：升序)
     */
    public List<PriceVolumeInfo> getAllAskVolumeInfos() {
        List<BigDecimal> sortedPrices = getSortedAskPrices();
        List<PriceVolumeInfo> result = new ArrayList<>();
        for (BigDecimal price : sortedPrices) {
            PriceVolumeInfo info = buildVolumeInfo(price, fdAsk);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }
}
