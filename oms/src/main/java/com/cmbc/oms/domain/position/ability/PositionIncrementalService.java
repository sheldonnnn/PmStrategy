package com.cmbc.oms.domain.position.ability;

import com.cmbc.oms.domain.event.RspTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.RspTraderQryStorageEvent;
import com.cmbc.oms.domain.order.model.ExecutionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author chendaqian
 * @date 2026/3/19
 * @time 10:25
 * @description 增量和比对持仓服务 - 专门处理增量更新和同步场景
 */
@Service
public class PositionIncrementalService {
    private static final Logger log = LoggerFactory.getLogger(PositionIncrementalService.class);

    private static final String POSITION = "POSITION";
    private static final String COMPARE_POSITION = "COMPARE_POSITION";

    @Autowired
    private IPositionManageService positionManageService;

    // 处理比对场景的现货持仓响应
    public void handleSpotPositionCompareResponse(RspTraderQryStorageEvent event) {
        positionManageService.spotToPosition(event, COMPARE_POSITION);
    }

    // 处理比对场景的期货持仓响应
    public void handleContractPositionCompareResponse(RspTraderPosiAllQryEvent event) {
        positionManageService.contractToPosition(event, COMPARE_POSITION);
    }

    /**
     * 监听成交订单更新事件并更新持仓
     */
    public void handleIncrementOrderEvent(ExecutionReport event) {
        log.debug("收到订单更新事件，订单ID：{}，订单状态：{}", event.getOrderId(), event.getStatus());
        try {
            // 获取订单相关信息
            String orderId = event.getOrderId();
            positionManageService.updatePositionByOrder(event);
            log.info("订单更新事件java持仓服务处理完成，订单ID：{}", orderId);
        } catch (Exception e) {
            log.error("处理订单更新事件失败", e);
            throw e;
        }
    }
}
