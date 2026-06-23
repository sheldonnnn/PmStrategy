package com.cmbc.mds.test.controller;

import com.cmbc.mds.forex.engine.port.MarketDataQueueGateway;
import com.cmbc.mds.forex.quotes.dto.Depth;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestSubscriptionController {

    @Autowired
    private MarketDataQueueGateway queueGateway;

    /**
     * 发送模拟行情数据到清洗队列 (模拟 Clean Channel)
     * 对应原 Akka 架构中的: 发送 CleanMessage 到 CleanActor
     *
     *
     * 示例 POST 报文：
     * <pre>
     * {
     *   "actorId": "GS_USDCNY",
     *   "data": {
     *     "symbol": "USD/CNY",
     *     "source": "GS",
     *     "provider": "GS",
     *     "midPrices": [7.2100],
     *     "bidPrices": [7.2000],
     *     "askPrices": [7.2200],
     *     "bidQuantities": [1000000],
     *     "askQuantities": [1000000],
     *     "transactionTime": "2026-05-25T10:00:00.000Z"
     *   }
     * }
     * </pre>
     *
     * @param request 包含目标通道ID(cleanId) 和 行情数据(depth)
     * @return 操作结果
     */
    @PostMapping("/send-message")
    public String sendMessage(@RequestBody SendMessageRequest request) {
        try {
            // 参数校验
            if (request.getActorId() == null || request.getData() == null) {
                return "Failed: actorId (cleanId) and data (depth) are required.";
            }

            queueGateway.pushToClean(request.getActorId(), request.getData());

            return "Message pushed to Engine [CleanQueue] successfully. Channel: " + request.getActorId();
        } catch (Exception e) {
            return "Failed to send message: " + e.getMessage();
        }
    }

    /**
     * 发送模拟聚合消息到聚合队列 (模拟 Merge Channel)
     * (可选扩展：如果你需要直接测试聚合逻辑，可以增加此接口)
     */
    /**
     * 发送模拟聚合消息到聚合队列 (模拟 Merge Channel)
     *
     * 示例 POST 报文：
     * <pre>
     * {
     *   "mergeId": "MD:MERGE:[GS.GS]:USD/CNY",
     *   "data": {
     *     "symbol": "USD/CNY",
     *     "source": "GS",
     *     "provider": "GS",
     *     "midPrices": [7.2100],
     *     "bidPrices": [7.2000],
     *     "askPrices": [7.2200],
     *     "bidQuantities": [1000000],
     *     "askQuantities": [1000000],
     *     "transactionTime": "2026-05-25T10:00:00.000Z"
     *   }
     * }
     * </pre>
     */
    @PostMapping("/send-merge")
    public String sendMergeMessage(@RequestBody SendMergeRequest request) {
         try {
             if (request.getMergeId() == null || request.getData() == null) {
                 return "Failed: mergeId and data are required.";
             }
             com.cmbc.mds.forex.quotes.dto.MergeMessage msg = new com.cmbc.mds.forex.quotes.dto.MergeMessage(
                     request.getMergeId(), null, request.getData());
             queueGateway.pushToMerge(request.getMergeId(), msg);
             return "Message pushed to Engine [MergeQueue] successfully. MergeId: " + request.getMergeId();
         } catch (Exception e) {
             return "Failed to send merge message: " + e.getMessage();
         }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendMessageRequest {
        // 在新架构下，actorId 对应 CleanChannelId (例如 "UBS_USDCNY")
        private String actorId;

        // 【修改】直接使用 Depth 对象，替代已删除的 CleanMessage
        private Depth data;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendMergeRequest {
        private String mergeId;
        private Depth data;
    }
}
