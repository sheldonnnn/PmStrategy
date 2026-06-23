package com.cmbc.strategy.integration;

import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;

import java.util.List;

public interface IMarketDataService {

    public void subscribe(List<SubscribeRequest> subscribeReqs, String instanceId, String userId); // 订阅
    public void unsubscribe(List<SubscribeRequest> subscribeReqs); // 退订
    public PloyPrices getPloyPrice(String symbol, String exchId, String counterParty); // 获取快照
}
