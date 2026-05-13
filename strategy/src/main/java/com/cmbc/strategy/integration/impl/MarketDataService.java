package com.cmbc.strategy.integration.impl;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.mds.service.MergeQuotesCacheService;
import com.cmbc.strategy.domain.model.market.PloyPrices;
import com.cmbc.strategy.domain.model.market.SubscribeRequest;
import com.cmbc.strategy.integration.IMarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 行情服务实现类
 */
@Slf4j
@Component
public class MarketDataService implements IMarketDataService {

    @Autowired
    private StrategySubscriptionController strategySubscriptionController;

    @Autowired
    private MergeQuotesCacheService mergeQuotesCacheService;

    @Override
    public void subscribe(List<SubscribeRequest> subscribeReqs, String instanceId, String userId) {
        // 订阅格式转换为mds格式
        List<StrategySubReq> subscribeRequests = collectSubscriptions(subscribeReqs, instanceId, userId);
        log.info("策略发送订阅请求: {}", JSONObject.toJSONString(subscribeRequests));
        strategySubscriptionController.addBatchStrategySubscription(subscribeRequests);
    }

    @Override
    public void unsubscribe(List<SubscribeRequest> subscribeReqs) {
        // 暂未实现或根据业务逻辑补充
    }

    @Override
    public PloyPrices getPloyPrice(String symbol, String exchId, String counterparties) {
        // 1. 解析对手方列表
        List<String> providers = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        if ((counterparties == null || counterparties.trim().isEmpty()) || (exchId == null || exchId.trim().isEmpty())) {
            return null;
        } else {
            // 关键：按"#"拆分，并去除每一项的空格
            providers = Arrays.asList(counterparties.split("#"));
            sources = Arrays.asList(exchId.split("#"));
        }

        return mergeQuotesCacheService.getPolyPrices(sources, providers, symbol);
    }

    /**
     * // 订阅格式转换为mds格式
     */
    private List<StrategySubReq> collectSubscriptions(List<SubscribeRequest> subscribeReqs, String instanceId, String userId) {
        List<StrategySubReq> subscribeRequests = new ArrayList<>();

        for (SubscribeRequest subReq : subscribeReqs) {
            // 基础防空
            if (subReq.getSymbol() == null || subReq.getCounterParty() == null) continue;

            String counterparties = subReq.getCounterParty(); // 获取对手方配置
            String exchId = subReq.getExchId(); // 获取交易所信息

            // 1. 解析对手方列表
            List<String> providers = new ArrayList<>();
            List<String> sources = new ArrayList<>();

            if ((counterparties == null || counterparties.trim().isEmpty()) || (exchId == null || exchId.trim().isEmpty())) {
                continue;
            } else {
                // 关键：按"#"拆分
                providers = Arrays.asList(counterparties.split("#"));
                sources = Arrays.asList(exchId.split("#"));
            }

            // 订阅请求组装
            StrategySubReq req = new StrategySubReq();
            req.setStrategyId(instanceId);
            req.setProviders(providers);
            req.setSources(sources);
            req.setTraderId(userId);
            req.setSymbol(subReq.getSymbol());
            subscribeRequests.add(req);
        }

        return subscribeRequests;
    }
}

