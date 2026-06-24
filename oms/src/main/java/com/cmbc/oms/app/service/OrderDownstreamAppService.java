package com.cmbc.oms.app.service;

import com.cmbc.oms.controller.dto.StrategyOrder;
import com.cmbc.oms.domain.event.ExceptionNewOrderEvent;
import com.cmbc.oms.domain.event.MgapIncrementalOrderEvent;
import com.cmbc.oms.domain.event.OrderUpdateEvent;
import com.cmbc.oms.domain.exception.DownstreamProcessException;
import com.cmbc.oms.domain.exception.ExceptionNotificationService;
import com.cmbc.oms.domain.facade.apama.SendEventToApama;
import com.cmbc.oms.domain.facade.strategy.ExecutionReportEvent;
import com.cmbc.oms.domain.order.ability.factory.ExecutionReportMapper;
import com.cmbc.oms.domain.order.ability.factory.OrderUpdateFactory;
import com.cmbc.oms.domain.order.ability.service.ChildOrderUpdateService;
import com.cmbc.oms.domain.order.ability.service.OrderLifecycleService;
import com.cmbc.oms.domain.order.ability.service.OrderSequentialProcessor;
import com.cmbc.oms.domain.order.entity.NewOrder;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import com.cmbc.oms.domain.order.model.enums.EventActionType;
import com.cmbc.oms.domain.order.model.enums.OldOrderStatusEnum;
import com.cmbc.oms.domain.order.model.enums.OrderStatus;
import com.cmbc.oms.domain.position.ability.PositionIncrementalService;
import com.cmbc.oms.domain.position.ability.QuantPositionManager;
import com.cmbc.oms.infrastructure.cache.OrderCacheManager;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * @author chendaqian
 * @date 2026/1/27
 * @time 16:50
 * @description 订单下行链路处理
 */
@Service
public class OrderDownstreamAppService {

    private final Logger log = LoggerFactory.getLogger(OrderDownstreamAppService.class);

    @Autowired
    private ChildOrderUpdateService childOrderUpdateService;
    @Autowired
    private SendEventToApama sendEventToApama;
    @Autowired
    private OrderUpdateFactory factory;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private OrderLifecycleService orderLifecycleService;
    @Autowired
    private OrderCacheManager orderCacheManager;
    @Autowired
    private QuantPositionManager quantPositionManager;
    @Autowired
    private ExecutionReportMapper executionReportMapper;
    @Autowired
    private PositionIncrementalService positionIncrementalService;
    @Autowired
    private ExceptionNotificationService exceptionPushService;
    @Autowired
    private List<AbstractExecutionHandler> handlers;

    // 为每个订单维护一个单独的处理队列和线程
    private final OrderSequentialProcessor processor = new OrderSequentialProcessor();

    public void handleOrderUpdate(OrderUpdateEvent event){
        ExecutionReport executionReport;
        try {
            // 事件转化为对象
            executionReport = executionReportMapper.toBo(event);
            OrderUpdate(executionReport);
        }catch (Exception e){
            log.error("Error in handleOrderUpdate for order: {}", event.getOrderId(), e);
            exceptionPushService.pushExceptionInfo(event.getExtraParams().get("StrategyInstanceID"),
                    event.getExtraParams().get("UserName"), e.getMessage(), 3, event.toString());
        }
    }

    public void OrderUpdate(ExecutionReport executionReport ){
        if(!needProcess(executionReport)){
            return;
        }
        String orderId = executionReport.getOrderId();
        // 提交到异步队列中处理
        processor.submit(orderId, () -> executeDownstreamPipeline(executionReport));
    }

    // 异常订单事件
    public void handleExceptionReport(ExceptionNewOrderEvent exceptionNewOrderEvent) {
        String orderId = exceptionNewOrderEvent.getOrderId();
        String status = exceptionNewOrderEvent.getDealType();
        // 处理状态 0. 未处理, 1. 人工干预-拒单, 2. 人工干预-成交, 3 人工干预-撤单, 4. 系统恢复处理
        if(!StringUtils.hasText( orderId) || !StringUtils.hasText(status)) return;
        if(!orderCacheManager.getUnFinishedChildOrder().containsKey(orderId)) return;
        NewOrder exCeOrder;
        // 提交到异步队列中处理
        switch ( status ){
            case "1": // 拒单处理 将订单底层标记为异常，且发完拒单后抛给撤单等操作
                // newOrder需要增加一个异常状态，用于锁定，等待异常订单的后续处理反馈 1/2/3/4
                exCeOrder = orderCacheManager.getChildOrder(orderId);
                exCeOrder.setExceptionFlag(true);
                orderCacheManager.cacheUnFinishedChildOrder(orderId,exCeOrder);
                childOrderUpdateService.updateExceptionFlag(orderId, true);
                break;
            case "4": // 拒单处理 抛给后端拒单状态，因为目前1、2、3 apama会发orderUpdate事件，这里不用处理，重构oms后需要实现如下
//                ExecutionReport executionReport = createExceptionReport(exceptionNewOrderEvent);
//                executionReport.setStatus(OrderStatus.FAILED.getStatusCode());
//                executionReport.setApamaStatus(OldOrderStatusEnum.REJECT_ORDER.getStatusCode());
//                OrderUpdate(executionReport);
                break;
            case "2": // 成交处理
//                ExecutionReport executionReport2 = createExceptionReport(exceptionNewOrderEvent);
//                executionReport2.setStatus(OrderStatus.FILLED.getStatusCode());
//                executionReport2.setApamaStatus(OldOrderStatusEnum.ENTRUST_DEAL_ALL.getStatusCode());
//                OrderUpdate(executionReport2);
                break;
            case "3": // 撤单处理
//                ExecutionReport executionReport3 = createExceptionReport(exceptionNewOrderEvent);
//                executionReport3.setStatus(OrderStatus.CANCELLED.getStatusCode());
//                executionReport3.setApamaStatus(OldOrderStatusEnum.ENTRUST_WITHDRAWAL_SUCCESS.getStatusCode());
//                OrderUpdate(executionReport3);
                break;
            case "0": // 系统恢复处理，将订单同步动态订单表
                // 导致动态列表恢复成正常订单
                NewOrder normalOrder = orderCacheManager.getChildOrder(orderId);
                normalOrder.setExceptionFlag(false);
                orderCacheManager.cacheUnFinishedChildOrder(orderId,normalOrder);
                childOrderUpdateService.updateExceptionFlag(orderId, false);
                break;
        }
    }

    /**
     * 下行链路统一调度入口
     */
    private void executeDownstreamPipeline(ExecutionReport report) {
        String orderId = report.getOrderId();
        try{
            // 1. 订单状态转换为新OMS字典 (重构后去掉 todo ，应该由各个handler实现该实现)
            String newStatus = orderLifecycleService.getNewOrderStatus(report);
            report.setStatus(newStatus);
            // 从缓存中恢复该当前系统里的基底状态，作为状态机的 oldStatus 供观察
            String oldStatus = orderCacheManager.getChildOrder(report.getOrderId()).getStatus();
            report.setOldStatus(oldStatus != null ? oldStatus : OrderStatus.NEW.getStatusCode());
            // 订单状态更新以及持仓策略 (只处理Java端现有的下行订单, 以及管理台手工平仓单)
            if (isMGAPhedOrder(report)) {
                // 1. 业务分发路由 (ActionType)
                String actionType = getActionType(report.getApamaStatus(), newStatus).name();
                report.setActionType(actionType);

                // 2. 策略模式匹配对应的子处理器
                AbstractExecutionHandler handler = handlers.stream()
                        .filter(h -> h.supports(actionType))
                        .findFirst()
                        .orElse(null);

                if (handler == null) {
                    log.error("未找到 ActionType 为 {} 的处理器，拒绝执行：{}", actionType, report.getOrderId());
                    return;
                }

                // 3. 执行核心业务与状态机转换
                handler.handle(report);

                // 4. 驱动母单生命周期及量化子单联动
                if (report.getStrategyOrderId() != null) {
                    orderLifecycleService.updateParentOrderStatus(report.getStrategyOrderId(), report.getStatus());
                }

                // 5. 通知策略(含头寸、Todo持仓更新) --
                eventPublisher.publishEvent(new ExecutionReportEvent(this, report, actionType));

                // 6. 撤销状态订单缓存 ----
                if (Objects.requireNonNull(OrderStatus.fromStatusCode(report.getStatus())).isFinal()) {
                    orderCacheManager.removeOrderCache(orderId);
                    processor.onOrderFinalized(orderId); // 清理队列
                }
                // 7. 撤销母单动态表单---
                if(report.getStrategyOrderId() != null){
                    // 撤销母单下行单下拉 todo 重构后去掉
                    StrategyOrder strategyOrder = orderCacheManager.getParentRequestCache(report.getStrategyOrderId());
                    if (strategyOrder != null && strategyOrder.isEnd() && strategyOrder.getNewOrderList() != null) {
                        handleFinalOrder(strategyOrder.getOrderId());
                    }
                }
            }else{
                // 排除Mapama账单（因为上行已经经过java，下行拒单不需要处理） todo 重构后去掉
                if (isSpecialOrder(report)) {
                    log.info("持仓更新结果处理 (处理apama端下行订单): {}", orderId);
                    handlePositionPersistenceAndChange(report);
                }
            }
        }catch(DownstreamProcessException e){
            log.error("下行链路dispatchReport发生异常：{}，回落详情：{}", orderId, report, e);
            // 是否增加标记和/手工处理 todo
            // 发送前端
            pushMsgToWeb(report, "下行链路dispatchReport发生异常:"+e.getMessage(), 3);
        }catch (Exception e){ // NPE, IndexOutOfBoundsException 等等 未进行入状态机转化
            log.error("下行链路dispatchReport发生未知异常：{}, 回落详情：{}", orderId, report, e);
            pushMsgToWeb(report, "下行链路dispatchReport发生未知异常:"+e.getMessage(), 3);
        } catch (Throwable e){ // OOM 等
            log.error("下行链路dispatchReport发生系统级异常：{}, 回落详情：{}", orderId, report, e);
            pushMsgToWeb(report, "下行链路dispatchReport发生系统级异常:"+e.getMessage(), 3);
        }
    }


    private EventActionType getActionType(String apamaStatus, String targetStatus) {
        // 拒单处理
        if (OldOrderStatusEnum.ENTRUST_WITHDRAWAL_FAILURE.getStatusCode().equals(apamaStatus)
                || OldOrderStatusEnum.ENTRUST_RISK_REJECT_CANCEL.getStatusCode().equals(apamaStatus)
                || OldOrderStatusEnum.ENTRUST_WITHDRAWAL_REJECT.getStatusCode().equals(apamaStatus)
        ) return EventActionType.CANCEL_REJECT;
        // 委报确认
        if (OrderStatus.CONFIRMED.getStatusCode().equals(targetStatus)) return EventActionType.ACK;
        // 成交
        if (OrderStatus.PARTIAL_FILL.getStatusCode().equals(targetStatus)
                || OrderStatus.FILLED.getStatusCode().equals(targetStatus)) return EventActionType.MATCH;
        // 撤单成功
        if (OrderStatus.CANCELLED.getStatusCode().equals(targetStatus)
                || OrderStatus.PARTIAL_CANCELLED.getStatusCode().equals(targetStatus)) return EventActionType.CANCEL;
        // 拒单
        if (OrderStatus.FAILED.getStatusCode().equals(targetStatus)) return EventActionType.REJECT; // 外部拒单
        // 内部拒单
        if (OrderStatus.INTERNAL_FAILED.getStatusCode().equals(targetStatus)) return EventActionType.IN_REJECT; // 内部拒单
        throw new IllegalArgumentException("Unknown target status: " + targetStatus);
    }

    // 持仓持久化以及变更（java + 管理台对冲子盘计算）
    private void handlePositionPersistenceAndChange(ExecutionReport executionReport) {
        try{
            String orderId = executionReport.getOrderId();
            // 判断是否为java端新订单
            if(orderCacheManager.getChildStatusMap().containsKey(orderId)){
                // 如果订单存在于缓存中，则执行持仓更新
                NewOrder newOrder = orderCacheManager.getUnFinishedChildOrder().get(orderId);
                String openingClosingType = newOrder.getOpeningClosingType();
                MgapIncrementalOrderEvent incrementalOrderManageEvent =
                        factory.createMgapIncrementalOrderManageEventUpdate(executionReport, openingClosingType);
                sendEventToApama.sendEventToApama(incrementalOrderManageEvent);
                log.info("发送增量订单到APAMA端完成持仓：{}", incrementalOrderManageEvent);
            }
            // java+管理台对冲子盘计算的进行持仓服务增量更新
            positionIncrementalService.handleIncrementOrderEvent(executionReport);
        }catch (Exception e){
            log.error("handlePositionPersistenceAndChange异常：{}", executionReport.getOrderId(), e);
            exceptionPushService.pushExceptionInfo(executionReport.getStrategyInstanceID(),
                    executionReport.getUserName(), "handlePositionPersistenceAndChange异常:" + e.getMessage(),
                    3, "POSITION_PERSISTENCE_AND_CHANGE", executionReport);
        }
    }

    // 判断是否为内部挂单（java+手工apama）
    private boolean isMGAPhedOrder(ExecutionReport executionReport) {
        String positionTagCode = executionReport.getPositionTagCode();
        return BusinessConstant.ORDER_TAG_TYPE_MGAPHEDGE.equals(positionTagCode);
    }

    // 是否为特殊处理下行订单（主要是apama侧 非新订单）
    private boolean isSpecialOrder(ExecutionReport executionReport){
        // 拒单
        return !OrderStatus.FAILED.getStatusCode().equals(executionReport.getStatus());
    }

    private boolean needProcess(ExecutionReport executionReport) {
        String orderId = executionReport.getOrderId();
        if(orderId == null){
            log.warn("OrderUpdateEvent has null orderId");
            return false;
        }
        if(executionReport.getApamaStatus() == null){
            log.warn("OrderUpdateEvent has null ErrorID");
            return false;
        }
        return true;
    }

    // 撤销母单动态表单下拉 --
    private void handleFinalOrder(String parentOrderId) {
        // 母单已经状态，先清理其中子单对于底层 jvm级锁
        orderCacheManager.getParentRequestCache(parentOrderId).clearListData();
        // 撤销母单动态表单
        Thread.ofVirtual().name("FinalOrderCleanup-" + parentOrderId).start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));//延迟5s,避免重复触发/且序报文导致 npe
                orderCacheManager.removeOrderCache(parentOrderId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e){
                log.error("延迟线程handleFinalOrder异常, parentOrderId: {}", parentOrderId, e);
            }
        });
    }

    // 异常/拒单等信息推送给web端
    private void pushMsgToWeb(ExecutionReport executionReport, String errorMsg,int grade) {
        exceptionPushService.pushExceptionInfo(executionReport.getStrategyInstanceID(),
                executionReport.getUserName(),errorMsg, grade, "", executionReport.toString());
    }

    // 创建下行新报告，包装所有异常订单使用
    private ExecutionReport createExceptionReport(ExceptionNewOrderEvent exceptionNewOrder){
        ExecutionReport report = new ExecutionReport();
        report.setOrderId(exceptionNewOrder.getOrderId());
        report.setStrategyInstanceID(exceptionNewOrder.getStrategyInstanceID());
        report.setSide(exceptionNewOrder.getSide());
        report.setSymbol(exceptionNewOrder.getSymbol());
        report.setCurrency(exceptionNewOrder.getExtraParams().getOrDefault("Currency", ""));
        report.setOldStatus(exceptionNewOrder.getDealType());
        report.setOrderQty(new BigDecimal(exceptionNewOrder.getOrderQty()));
        report.setPrice(new BigDecimal(exceptionNewOrder.getPrice()));
        report.setDomesticType(exceptionNewOrder.getDomesticType());
        report.setDeliveryDate(exceptionNewOrder.getExtraParams().getOrDefault("EndDeliveryDate", ""));
        report.setExchId(exceptionNewOrder.getExtraParams().getOrDefault("ExchCode", ""));
        report.setInventoryType(exceptionNewOrder.getExtraParams().getOrDefault("InventoryType", ""));
        report.setUnit(new BigDecimal(exceptionNewOrder.getExtraParams().getOrDefault("Unit", "0")));
        report.setTagName(exceptionNewOrder.getExtraParams().getOrDefault("PositionTagName", ""));
        report.setSystemId(exceptionNewOrder.getExtraParams().getOrDefault("SystemId", ""));
        report.setTick(exceptionNewOrder.getExtraParams().getOrDefault("Tick", ""));
        report.setVarietyId(exceptionNewOrder.getExtraParams().getOrDefault("VarietyId", ""));
        report.setAccuracy(exceptionNewOrder.getExtraParams().getOrDefault("Accuracy", "0"));
        report.setBusinessType(exceptionNewOrder.getExtraParams().getOrDefault("PositionTagCode", ""));
        report.setCounterParty(exceptionNewOrder.getServiceId());

        return report;
    }
}
