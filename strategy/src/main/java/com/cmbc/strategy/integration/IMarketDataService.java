package com.cmbc.strategy.integration;

import com.cmbc.strategy.domain.model.market.Depth;

public interface IMarketDataService {

    public void subscribe(String symbol);           // 订阅
    public void unsubscribe(String symbol);         // 退订
    public Depth getMarketDataSnapshot(String symbol);         // 获取快照

}
