package com.cmbc.oms.app.service;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.controller.dto.StrategyOrderRes;
import com.cmbc.oms.domain.event.NewOrderEvent;
import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.domain.exception.PositionCheckException;
import com.cmbc.oms.domain.exposure.service.QuantPositionManager;
import com.cmbc.oms.domain.facade.apama.SendEventToApama;
import com.cmbc.oms.domain.order.ability.factory.NewOrderEventFactory;
import com.cmbc.oms.domain.order.ability.factory.OrderFactory;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.order.model.enums.OldOrderStatusEnum;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.domain.position.ability.IPositionManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @author chendaqian
 * @date 2026/1/27
 * @time 16:48
 * @description 订单上行链路处理
 */
@Service
public class OrderUpstreamAppService {
    private final Logger log = LoggerFactory.getLogger(OrderUpstreamAppService.class);
    @Autowired
    private SendEventToApama sendEventToApama;
    @Autowired
    private QuantPositionManager quantPositionManager;
    @Autowired
    private NewOrderEventFactory newOrderEventFactory;
    @Autowired
    private OrderFactory orderFactory;
    @Autowired
    private IPositionManageService positionManageService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private OrderDownstreamAppService downstreamAppService;
    @Autowired
    private ExceptionNotificationService exceptionPushService;

    /**
     * 处理平盘订单 (含拆单处理)
     */
    public StrategyOrderRes handleNewOrder(StrategyOrder parentOrder) {
        try {
            // 1. 订单基础检查
            orderBaseCheck(parentOrder);

            // 2. 订单本地缓存与拆单
            List<NewOrder> newOrders = orderFactory.createChildOrder(parentOrder);
            if (CollectionUtils.isEmpty(newOrders)) {
                return StrategyOrderRes.fail("母单下单失败：拆分出的子单为空");
            }
            orderLifecycleService.initOrderLifecycle(parentOrder);

            // 3. 循环提交子订单 (将单笔处理逻辑抽出，防止异常互相干扰)
            for (NewOrder newOrder : newOrders) {
                processingSingleChildOrder(newOrder);
            }
            // 全部受理完毕，返回成功。真正的成功与否由下行异步通知策略
            return StrategyOrderRes.success();
        } catch (Exception e) {
            log.error("母单处理全局系统异常", e);
            exceptionPushService.pushExceptionInfo(parentOrder.getInstanceId(),
                    parentOrder.getUserId(), "母单下单失败: " + e.getMessage(),
                    2, JSONObject.toJSONString(parentOrder));
            return StrategyOrderRes.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 处理单笔子订单的发送逻辑
     */
    private void processingSingleChildOrder(NewOrder newOrder) {
        // 【核心】: 局部冻结上下文标记
        boolean isPositionFrozen = false;
        String parentOrderId = newOrder.getParentOrderId();
        if(parentOrderId == null) return;
        try {
            log.debug("准备处理发单: {}", newOrder.getOrderId());
            // 1. 事前风控与持仓校验 (如果不通过会抛出业务异常)
            positionManageService.newOrderCheck(newOrder);
            // 2. 冻结持仓/资金/头寸
            positionManageService.freezePosition(newOrder); // 持仓冻结
            quantPositionManager.freezePosition(newOrder); // 头寸冻结
            isPositionFrozen = true; // 标记当前前子单已产生资产冻结
            // 3. 构建发单事件
            NewOrderEvent newOrderEvent = newOrderEventFactory.createNewOrderEvent(newOrder);
            // 4. 订单持久化 是否需要异步 todo
            childOrderUpdateService.persistOrder(parentOrderId,newOrder);
            // 5. 发送订单到 Apama-OMS
            log.info("发送订单到apama-OMS: {}", JSONObject.toJSONString(newOrderEvent));
            sendEventToApama.sendEventToApama(newOrderEvent);
        } catch (PositionCheckException e) {
            // 捕获业务校验异常 (如持仓不足)，此时 isPositionFrozen 必定为 false
            log.warn("子单事前风控校验拒绝: {}", e.getMessage());
            exceptionPushService.pushExceptionInfo(newOrder.getStrategyInstanceID(),
                    newOrder.getUserName(), "子单事前风控校验拒绝: " + e.getMessage(),
                    2, "", JSONObject.toJSONString(newOrder));
            triggerInternalRejectLoopback(newOrder, "业务校验拒单:" + e.getMessage(), isPositionFrozen);
        } catch (Exception e) {
            // 捕获系统发送异常 (如网络中断)，此时 isPositionFrozen 可能为 true
            log.error("子单系统发送异常", e);
            exceptionPushService.pushExceptionInfo(newOrder.getStrategyInstanceID(),
                    newOrder.getUserName(), "子单系统发送异常拒单: " + e.getMessage(),
                    2, "", JSONObject.toJSONString(newOrder));
            triggerInternalRejectLoopback(newOrder, "系统拒单:" + e.getMessage(), isPositionFrozen);
        }
    }

    /**
     * 触发内部拒单事件，统一调用下行引擎
     */
    private void triggerInternalRejectLoopback(NewOrder newOrder, String reason, boolean isPositionFrozen) {
        ExecutionReport rejectEvent = new ExecutionReport(newOrder.getOrderId(), newOrder);
        rejectEvent.setStatus(OrderStatus.INTERNAL_FAILED.getStatusCode());
        rejectEvent.setApamaStatus(OldOrderStatusEnum.REJECT_ORDER.getStatusCode());
        rejectEvent.setStatusMsg(reason);
        rejectEvent.setPositionFrozen(isPositionFrozen); // 携带上下文：告诉下行到底要不要执行解冻
        // 将异常事件提交给下行统一处理器
        downstreamAppService.OrderUpdate(rejectEvent);
    }

    // 母单基础信息检查 TODO
    private void orderBaseCheck(StrategyOrder strategyOrder) {
        // 1.订单基础信息检查

        // 2.订单唯一性检查
    }
}
