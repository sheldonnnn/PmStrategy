package com.cmbc.oms.app.service;

import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.node.OrderStateMachineValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 18:23
 * @description 下行处理器基类
 */
@Slf4j
public abstract class AbstractExecutionHandler {

    @Autowired
    protected OrderStateMachineValidator stateMachineValidator;

    public abstract boolean supports(String actionType);

    // 获取当前通道回报对应的标准业务目标状态
    protected abstract String getTargetStatus(ExecutionReport report);

    // 执行具体的业务计算、持仓或乱序补偿
    protected abstract void doHandle(ExecutionReport report);

    public void handle(ExecutionReport report) {
        // 1. 动态决策目标状态
        String targetStatus = getTargetStatus(report);

        // 2. 基于自研有向图进行合规性验证
        boolean isLegal = stateMachineValidator.isLegalTransition(report.getOldStatus(), targetStatus);
        if (!isLegal) {
            log.error("订单 {} 拒绝状态跳变！非法路径：{} -> {}", report.getOrderId(), report.getOldStatus(), targetStatus);
            throw new IllegalStateException("订单 " + report.getOrderId() + " 状态流转非法！");
        }

        // 3. 执行前置拦截 (对应原方案 PreStateChange 职责)
        report.setStatus(targetStatus);
        // 4. 执行核心子类业务
        doHandle(report);
    }

    private void preStateChange(ExecutionReport report) {
        // 原子落库持久化与更新分布式缓存
        log.info("订单 {} 状态准备变更 [{} -> {}] - 开始落盘", report.getOrderId(), report.getOldStatus(), report.getStatus());
    }
}
