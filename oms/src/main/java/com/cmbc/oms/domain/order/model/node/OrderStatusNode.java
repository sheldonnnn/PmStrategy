package com.cmbc.oms.domain.order.model.node;

import com.cmbc.oms.domain.order.model.enums.OrderStatus;

import java.util.HashSet;
import java.util.Set;

/**
 * @author chendaqian
 * @date 2026/3/4
 * @time 19:13
 * @description
 */
public class OrderStatusNode {
    private OrderStatus status;
    private Set<OrderStatusNode> children;

    public OrderStatusNode(OrderStatus status) {
        this.status = status;
        this.children = new HashSet<>();
    }

    public void addChild(OrderStatusNode child) {
        this.children.add(child);
    }

    public boolean canTransitionTo(OrderStatusNode target) {
        // 直接转换或通过子节点转换
        return this.children.contains(target); //目前使用强校验
        // ||this.children.stream().anyMatch(child -> child.canTransitionTo(target));
    }

    public OrderStatus getStatus() { return status; }

    public OrderStatusNode setStatus(OrderStatus status) {
        this.status = status;
        return this;
    }

    public Set<OrderStatusNode> getChildren() { return children; }

    public OrderStatusNode setChildren(Set<OrderStatusNode> children) {
        this.children = children;
        return this;
    }
}
