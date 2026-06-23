package com.cmbc.mds.forex.subscription.core.model.topic;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.subscription.dto.MergeBaseInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * [新增] 聚合行情主题
 * <p>
 * 对应原来的 DataLevel.MERGE，但增加了动态多源支持。
 * 该 Topic 代表基于一组特定提供商列表聚合后的行情。
 * <p>
 * 核心机制:
 * 构造时会对 providers 列表进行排序，确保 ["A", "B"] 和 ["B", "A"] 生成相同的 Key，
 * 从而共享同一个聚合通道和引用计数。
 */
@Getter
@Setter
public class MergeDataTopic implements SubscriptionTopic {

    // 标识为聚合数据
    public static final String TYPE = "MARKET_DATA_MERGE";

    // source和provider共同构成一个完整的行情源：UBS.UBS FXALL.UBS FXALL.JPMC
    private final List<String> sources; // 价格源列表 UBS FXALL
    private final List<String> providers; // 交易对手列表 UBS JPMC
    private final String symbol; // 货币对，外资行使用斜杠格式：EUR/USD USD/JPY；贵金属使用原始格式：AU9999

    // 缓存 Key，避免每次 getTopicKey() 时重复进行排序和拼接，提升性能
    private final String cachedKey;

    /**
     * 构建聚合行情主题
     * 
     * @param symbol    货币对 (例如: "EURUSD")
     * @param providers 参与聚合的源列表 (例如: ["UBS", "HSBC"])
     */
    public MergeDataTopic(List<String> sources, List<String> providers, String symbol) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Sources list cannot be empty for MergeTopic");
        }
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Providers list cannot be empty for MergeTopic");
        }
        if (sources.size() != providers.size()) {
            throw new IllegalArgumentException("Sources and providers list must have the same size");
        }

        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");

        // 使用统一的配对排序逻辑，保证内部列表存储与组合 Key 生成时的排序基准绝对一致
        List<SourceProviderPair> pairList = getSortedPairs(sources, providers);

        int size = pairList.size();
        List<String> sortedSources = new ArrayList<>(size);
        List<String> sortedProviders = new ArrayList<>(size);
        for (SourceProviderPair pair : pairList) {
            sortedSources.add(pair.source);
            sortedProviders.add(pair.provider);
        }

        // 3. 转为不可变且强制有序的列表，防止外部修改且保障对齐
        this.sources = Collections.unmodifiableList(sortedSources);
        this.providers = Collections.unmodifiableList(sortedProviders);

        // 4. 预先构建并缓存 Key
        this.cachedKey = buildKey(this.sources, this.providers, this.symbol);
    }

    /** 内部使用的配对排序包装器 */
    private static class SourceProviderPair implements Comparable<SourceProviderPair> {
        final String source;
        final String provider;
        final String combined;

        SourceProviderPair(String source, String provider) {
            this.source = source;
            this.provider = provider;
            this.combined = source + "." + provider;
        }

        @Override
        public int compareTo(SourceProviderPair other) {
            return this.combined.compareTo(other.combined);
        }
    }

    /**
     * 统一提取的配对和排序逻辑
     */
    private static List<SourceProviderPair> getSortedPairs(List<String> sources, List<String> providers) {
        if (sources == null || providers == null || sources.size() != providers.size()) {
            throw new IllegalArgumentException("Sources and providers list must be non-null and have the same size");
        }
        int size = sources.size();
        List<SourceProviderPair> pairList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pairList.add(new SourceProviderPair(sources.get(i), providers.get(i)));
        }
        Collections.sort(pairList);
        return pairList;
    }

    /**
     * [重构] 构建唯一 Key
     * 逻辑:
     * 1. 将 source 和 provider 按索引配对 (使用统一的排序方法)
     * 2. 拼接生成 Key
     * * 目的: 确保 {(A,1), (B,2)} 和 {(B,2), (A,1)} 生成相同的 Key
     */
    public static String buildKey(List<String> sources, List<String> providers, String symbol) {
        // 调用统一的配对排序逻辑
        List<SourceProviderPair> pairList = getSortedPairs(sources, providers);

        int size = pairList.size();
        List<String> pairs = new ArrayList<>(size);
        for (SourceProviderPair pair : pairList) {
            pairs.add(pair.combined);
        }

        // 格式: MD:MERGE:[{S1.P1},{S2.P2}]:{SYMBOL}
        // 例如: MD:MERGE:[FXALL.JPMC,UBS.UBS]:EUR/USD
        String pairStr = String.join(",", pairs);
        return "MD:MERGE:" + "[" + pairStr + "]:" + symbol;
    }

    @Override
    public String getTopicKey() {
        // 直接返回构造时计算好的 Key
        return cachedKey;
    }

    /**
     * 根据 Topic Key 解析出 StrategySubReq 对象
     * Key 格式示例: MD:MERGE:[FXALL.JPMC,UBS.UBS]:EUR/USD
     */
    public static MergeBaseInfo parseKeyToReq(String key) {
        if (key == null || !key.startsWith(BaseConstants.MARKET_DATA_MERGE_KEY_PREFIX)) {
            throw new IllegalArgumentException("Invalid key format: " + key);
        }

        MergeBaseInfo req = new MergeBaseInfo();
        List<String> sources = new ArrayList<>();
        List<String> providers = new ArrayList<>();

        try {
            // 1. 定位关键分隔符的位置
            // 格式: MD:MERGE:[pairStr]:symbol
            int startBracketIndex = key.indexOf("[");
            int endBracketIndex = key.lastIndexOf("]");

            if (startBracketIndex == -1 || endBracketIndex == -1 || endBracketIndex < startBracketIndex) {
                throw new IllegalArgumentException("Key structure invalid: missing brackets");
            }

            // 2. 解析 Symbol (位于 "]:" 之后)
            // endBracketIndex 指向 ']', 所以 symbol 从 endBracketIndex + 2 开始 (跳过 ']:')
            String symbol = key.substring(endBracketIndex + 2);
            req.setSymbol(symbol);

            // 3. 解析 Source 和 Provider 组合部分 (位于 '[' 和 ']' 之间)
            String pairStr = key.substring(startBracketIndex + 1, endBracketIndex);

            if (pairStr != null && !pairStr.isEmpty()) {
                // 4. 按逗号分割每组配置
                String[] pairs = pairStr.split(",");

                for (String pair : pairs) {
                    // 5. 按点号分割 Source和Provider (注意转义 "\\.")
                    String[] sp = pair.split("\\.", 2);
                    if (sp.length == 2) {
                        sources.add(sp[0]);
                        providers.add(sp[1]);
                    } else {
                        // 容错处理，防止空字符串或格式错误
                        throw new IllegalArgumentException("Invalid pair format: " + pair);
                    }
                }
            }

            req.setSources(sources);
            req.setProviders(providers);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse key: " + key, e);
        }

        return req;
    }

    @Override
    public String getTopicType() {
        return TYPE;
    }

    /**
     * 重写 equals
     * 比较 cachedKey 即可，因为 Key 已经包含了 symbol 和排序后的 providers 信息
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergeDataTopic that = (MergeDataTopic) o;
        return Objects.equals(cachedKey, that.cachedKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cachedKey);
    }

    @Override
    public String toString() {
        return "MergeDataTopic{" +
                "key='" + cachedKey + '\'' +
                '}';
    }
}