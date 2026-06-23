package com.cmbc.mds.forex.subscription.controller;

import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import com.cmbc.mds.forex.subscription.dto.StrategySubQueryReq;
import com.cmbc.mds.forex.subscription.dto.StrategySubReq;
import com.cmbc.mds.forex.subscription.service.StrategySubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscription/strategy")
public class StrategySubscriptionController {

    @Autowired
    private StrategySubscriptionService strategySubscriptionService;

    @PostMapping("/add")
    public String addStrategySubscription(@RequestBody StrategySubReq request) {
        try {
            strategySubscriptionService.addStrategySubscription(
                    request.getSources(),
                    request.getProviders(),
                    request.getSymbol(),
                    request.getTraderId(),
                    request.getStrategyId()
            );
            return "Strategy subscription added successfully";
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @PostMapping("/batch/add")
    public String addBatchStrategySubscription(@RequestBody List<StrategySubReq> request) {
        try {
            strategySubscriptionService.addBatchStrategySubscription(request);
            return "Batch strategy subscription added successfully for " + request.size() + "Stages";
        } catch (Exception e) {
            return "Batch Failed: " + e.getMessage();
        }
    }

    @PostMapping("/remove")
    public String removeStrategySubscription(@RequestBody StrategySubReq request) {
        try {
            strategySubscriptionService.removeStrategySubscription(
                    request.getSources(),
                    request.getProviders(),
                    request.getSymbol(),
                    request.getStrategyId() // 注意：这里使用了StrategyId进行操作
            );
            return "Strategy subscription removed successfully";
        } catch (Exception e) {
            return "Failed to remove: " + e.getMessage();
        }
    }

    /**
     * [新增] 按StrategyId清理所有订阅
     */
    @PostMapping("/clear")
    public String clearStrategySubscriptions(@RequestBody StrategySubReq request) {
        if (request.getStrategyId() == null || request.getStrategyId().isEmpty()) {
            return "StrategyId is required";
        }
        strategySubscriptionService.removeAllSubscriptionsByStrategyId(request.getStrategyId());
        return "All subscriptions cleared for strategy: " + request.getStrategyId();
    }


    /**
     * 查询策略已订阅的行情
     */
    @PostMapping("/query")
    public List<SubscriptionTopic> queryStrategySubscriptions(@RequestBody StrategySubQueryReq request) {
        return strategySubscriptionService.getStrategySubscriptions(request.getStrategyId());
    }
}