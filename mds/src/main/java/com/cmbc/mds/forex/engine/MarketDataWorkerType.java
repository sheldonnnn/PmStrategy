package com.cmbc.mds.forex.engine;

import com.cmbc.mds.forex.common.constants.MetricConstants;

/**
 * 行情 worker 类型，统一维护日志名称和性能指标名称。
 */
public enum MarketDataWorkerType {

    CLEAN("清洗", MetricConstants.CLEAN_WORKER),
    MERGE("聚合", MetricConstants.MERGE_WORKER);

    private final String displayName;
    private final String metricName;

    MarketDataWorkerType(String displayName, String metricName) {
        this.displayName = displayName;
        this.metricName = metricName;
    }

    /**
     * 获取日志展示名称。
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 获取性能指标名称。
     */
    public String metricName() {
        return metricName;
    }
}
