package com.cmbc.mds.forex.subscription.controller;

import com.cmbc.mds.forex.subscription.core.model.topic.SubscriptionTopic;
import com.cmbc.mds.forex.subscription.dto.TraderSubQueryReq;
import com.cmbc.mds.forex.subscription.dto.TraderSubReq;
import com.cmbc.mds.forex.subscription.service.TraderSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscription")
public class TraderSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(TraderSubscriptionController.class);

    @Autowired
    private TraderSubscriptionService traderSubscriptionService;

    @PostMapping("/trader/add")
    public String addTraderSubscription(@RequestBody TraderSubReq request) {
        try {
            traderSubscriptionService.addTraderSubscription(
                    request.getSource(),
                    request.getProvider(),
                    request.getSymbol(),
                    request.getTraderId());
            return "Trader subscription added successfully";
        } catch (Exception e) {
            log.error("[TraderSubscription] 添加订阅失败: source={}, provider={}, symbol={}, traderId={}",
                    request.getSource(), request.getProvider(), request.getSymbol(), request.getTraderId(), e);
            return "Failed: " + e.getMessage();
        }
    }

    @PostMapping("/trader/batch/add")
    public String addBatchTraderSubscription(@RequestBody List<TraderSubReq> request) {
        try {
            traderSubscriptionService.addBatchTraderSubscription(request);
            return "Batch trader subscription added successfully for " + request.size() + " pairs";
        } catch (Exception e) {
            log.error("[TraderSubscription] 批量添加订阅失败: size={}", request.size(), e);
            return "Batch Failed: " + e.getMessage();
        }
    }

    @PostMapping("/trader/remove")
    public String removeTraderSubscription(@RequestBody TraderSubReq request) {
        try {
            traderSubscriptionService.removeTraderSubscription(
                    request.getSource(),
                    request.getProvider(),
                    request.getSymbol(),
                    request.getTraderId());
            return "Trader subscription removed successfully";
        } catch (Exception e) {
            log.error("[TraderSubscription] 删除订阅失败: source={}, provider={}, symbol={}, traderId={}",
                    request.getSource(), request.getProvider(), request.getSymbol(), request.getTraderId(), e);
            return "Failed to remove: " + e.getMessage();
        }
    }

    /**
     * [新增] 按TraderId清理所有订阅
     */
    @PostMapping("/trader/clear")
    public String clearTraderSubscriptions(@RequestBody TraderSubReq request) {
        if (request.getTraderId() == null || request.getTraderId().isEmpty()) {
            return "TraderId is required";
        }
        try {
            traderSubscriptionService.removeAllSubscriptionsByTraderId(request.getTraderId());
            return "All subscriptions cleared for trader: " + request.getTraderId();
        } catch (Exception e) {
            log.error("[TraderSubscription] 清理订阅失败: traderId={}", request.getTraderId(), e);
            return "Failed to clear: " + e.getMessage();
        }
    }

    @PostMapping("/trader/batch/remove")
    public String removeBatchTraderSubscription(@RequestBody List<TraderSubReq> request) {
        try {
            traderSubscriptionService.removeBatchTraderSubscription(request);
            return "Batch trader subscription removed successfully";
        } catch (Exception e) {
            log.error("[TraderSubscription] 批量删除订阅失败: size={}", request.size(), e);
            return "Batch remove failed: " + e.getMessage();
        }
    }

    /**
     * 查询交易员已订阅的行情
     */
    @PostMapping("/trader/query")
    public List<SubscriptionTopic> queryTraderSubscriptions(@RequestBody TraderSubQueryReq request) {
        return traderSubscriptionService.getTraderSubscriptions(request.getTraderId());
    }
}