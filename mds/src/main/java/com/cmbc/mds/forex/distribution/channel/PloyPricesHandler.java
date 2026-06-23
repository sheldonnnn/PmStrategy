package com.cmbc.mds.forex.distribution.channel;

import com.cmbc.mds.forex.quotes.dto.PloyPrices;

/**
 * 策略行情回调接口
 * <p>
 * 设计为函数式接口，调用方可直接使用 Lambda 或方法引用注册，零模板代码。
 * 配合 {@link com.cmbc.mds.forex.distribution.channel.impl.StrategyExecutionChannel}
 * 的具名注册机制使用，支持动态注销。
 */
@FunctionalInterface
public interface PloyPricesHandler {

    /**
     * 收到聚合行情时的回调
     *
     * @param ployPrices 聚合后的行情数据（快照，只读）
     */
    void onPloyPrices(PloyPrices ployPrices);
}
