package com.cmbc.strategy.integration;

import com.cmbc.strategy.domain.model.market.Depth;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;

import java.util.List;

public interface IMarketDataService {

    public void subscribe(List<SubscribeRequest> subscribeReqs, String instanceId, String userId);           // 订阅
    public void unsubscribe(List<SubscribeRequest> subscribeReqs);         // 退订
    public Depth getPloyPrice(String symbol, String exchId, String counterparties);         // 获取快照

}
