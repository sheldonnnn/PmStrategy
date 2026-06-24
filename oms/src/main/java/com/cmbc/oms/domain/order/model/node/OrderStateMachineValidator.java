package com.cmbc.oms.domain.order.model.node;

import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/4
 * @time 19:15
 * @description
 */
@Service
public class OrderStateMachineValidator {
    private final Map<String, OrderStatusNode> statusNodeMap;

    public OrderStateMachineValidator() {
        this.statusNodeMap = new HashMap<>();
        buildStateMachine();
    }

    private void buildStateMachine() {
        // 创建所有状态节点
        for (OrderStatus status : OrderStatus.values()) {
            if (statusNodeMap.containsKey(status.getStatusCode())) {
                throw new IllegalStateException("Duplicate statusCode: " + status.getStatusCode());
            }
            statusNodeMap.put(status.getStatusCode(), new OrderStatusNode(status));
        }

        // 建立状态转换关系
        // 以及各种可能的转换路径 todo：需要完善状态转换关系
        OrderStatusNode createdNode = statusNodeMap.get(OrderStatus.CREATED.getStatusCode());
        OrderStatusNode internalFailedNode = statusNodeMap.get(OrderStatus.INTERNAL_FAILED.getStatusCode());
        OrderStatusNode newNode = statusNodeMap.get(OrderStatus.NEW.getStatusCode());
        OrderStatusNode failedNode = statusNodeMap.get(OrderStatus.FAILED.getStatusCode());
        OrderStatusNode confirmedNode = statusNodeMap.get(OrderStatus.CONFIRMED.getStatusCode());
        OrderStatusNode partialFillNode = statusNodeMap.get(OrderStatus.PARTIAL_FILL.getStatusCode());
        OrderStatusNode filledNode = statusNodeMap.get(OrderStatus.FILLED.getStatusCode());
        OrderStatusNode cancelledNode = statusNodeMap.get(OrderStatus.CANCELLED.getStatusCode());
        OrderStatusNode partialCancelledNode = statusNodeMap.get(OrderStatus.PARTIAL_CANCELLED.getStatusCode());

        if (createdNode != null && newNode != null) {
            createdNode.addChild(newNode);
        }
        if (createdNode != null && internalFailedNode != null) {
            createdNode.addChild(internalFailedNode);
        }
        if (createdNode != null && failedNode != null) {
            createdNode.addChild(failedNode);
        }
        if (createdNode != null && confirmedNode != null) {
            createdNode.addChild(confirmedNode);
        }
        if (newNode != null && confirmedNode != null) {
            newNode.addChild(confirmedNode);
        }
        if (newNode != null && failedNode != null) {
            newNode.addChild(failedNode);
        }

        // 允许乱序补偿：从未确认(NEW)状态直接由于乱序成交跳变到成交状态，实际执行并不会允许执行
        if (newNode != null && partialFillNode != null) newNode.addChild(partialFillNode);
        if (newNode != null && filledNode != null) newNode.addChild(filledNode);

        if (confirmedNode != null && partialFillNode != null) {
            confirmedNode.addChild(partialFillNode);
            // 原订单委托确认 -> 撤单委托
            // confirmedNode.addChild(newNode);
            // 原订单委托确认 -> 撤单失败
            // confirmedNode.addChild(failedNode);
        }

        if (confirmedNode != null) {
            // 原订单委托确认 -> 撤单拒单 (仍维持状态不变)
            confirmedNode.addChild(confirmedNode);
        }

        if (confirmedNode != null && filledNode != null) {
            confirmedNode.addChild(filledNode);
        }
        if (confirmedNode != null && cancelledNode != null) {
            confirmedNode.addChild(cancelledNode);
        }
        if (partialFillNode != null) {
            // 部分成交 -> 撤单拒单 (仍维持状态不变)
            partialFillNode.addChild(partialFillNode);
        }
        if (partialFillNode != null && filledNode != null) {
            // 部分成交 -> 全部成交 (终态)
            partialFillNode.addChild(filledNode);
        }
        if (partialFillNode != null && partialCancelledNode != null) {
            // 部分成交 -> 部分撤销 (终态)
            partialFillNode.addChild(partialCancelledNode);
        }
    }

    public boolean isLegalTransition(String fromStatus, String toStatus) {
        OrderStatusNode fromNode = statusNodeMap.get(fromStatus);
        OrderStatusNode toNode = statusNodeMap.get(toStatus);

        if (fromNode == null || toNode == null) {
            return false;
        }

        // 从起始状态到目标状态是否可以转换
        return fromNode.canTransitionTo(toNode);
    }
}
