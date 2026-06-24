package com.cmbc.oms.domain.position.ability;

import com.cmbc.oms.domain.event.RspTraderPosiAllQryEvent;
import com.cmbc.oms.domain.event.RspTraderQryStorageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author chendaqian
 * @date 2026/3/19
 * @time 10:50
 * @description 专门处理Apama响应的监听服务，避免直接依赖PositionManageService
 */
@Service
public class ApamaResponseHandlerService {
    private static final Logger log = LoggerFactory.getLogger(ApamaResponseHandlerService.class);

    @Autowired
    private PositionInitService positionInitService;

    @Autowired
    private PositionIncrementalService positionIncrementalService;

    @Autowired
    private PositionRequestManager positionRequestManager;

    // 处理现货持仓响应
    public void handleSpotPositionResponse(RspTraderQryStorageEvent event) {
        String uniqueId = event.getUniqueID();

        // 根据请求ID判断处理场景
        if (positionRequestManager.isInitRequest(uniqueId)) {
            // 初始化场景
            log.info("执行初始化场景的现货库存处理逻辑,请求id：{}",uniqueId);
            positionInitService.handleSpotPositionResponse(event);
        } else if (positionRequestManager.isCompareRequest(uniqueId)) {
            // 比对场景
            log.info("执行比对场景的现货持仓处理逻辑,请求id: {}",uniqueId);
            positionIncrementalService.handleSpotPositionCompareResponse(event);
        } else {
            // 不是应用触发的请求响应
        }
    }

    // 处理期货持仓响应
    public void handleContractPositionResponse(RspTraderPosiAllQryEvent event) {
        String uniqueId = event.getUniqueID();

        // 根据请求ID判断处理场景
        if (positionRequestManager.isInitRequest(uniqueId)) {
            // 初始化场景
            log.info("执行初始化场景的期货持仓处理逻辑,请求id：{}",uniqueId);
            positionInitService.handleContractPositionResponse(event);
        } else if (positionRequestManager.isCompareRequest(uniqueId)) {
            // 比对场景
            log.info("执行比对场景的期货持仓处理逻辑,请求id: {}",uniqueId);
            positionIncrementalService.handleContractPositionCompareResponse(event);
        } else {
            // 不是应用触发的请求响应
        }
    }
}
