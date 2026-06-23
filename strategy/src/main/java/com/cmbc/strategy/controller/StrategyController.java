package com.cmbc.strategy.controller;

import com.cmbc.strategy.configuration.BusinessException;
import com.cmbc.strategy.domain.dto.*;
import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;
import com.cmbc.strategy.domain.response.QueryStrategyInstanceResponse;
import com.cmbc.strategy.integration.IHedgeStrategyWebSocketService;
import com.cmbc.strategy.engine.hedge.manager.HedgeStrategyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/goldHedge/strategy")
@Slf4j
public class StrategyController {

    @Autowired
    private HedgeStrategyManager strategyManager;
    
    @Autowired
    private IHedgeStrategyWebSocketService goldHedgeStrategyWebSocketService;

    /**
     * 启动平盘策略实例
     */
    @PostMapping("/start")
    public ApiResponse startStrategy(@RequestBody HedgeStrategyRequest request) {
        log.info("Receive start request: {}",
                request);
        ApiResponse apiResponse = new ApiResponse();
        try {
            // 调用核心管理器进行加载和启动
            strategyManager.startStrategy(request);
            apiResponse.setCode("0");
            apiResponse.setMessage("策略启动成功!");
        } catch (BusinessException ex) {
            log.error("策略启动失败:{} ", ex.getMessage());
            apiResponse.setCode("9");
            apiResponse.setMessage(ex.getMessage());
        } catch (Exception e) {
            log.error("策略启动失败!! 策略实例ID:{} ", request.getInstanceId(), e);
            apiResponse.setCode("1");
            apiResponse.setMessage(e.getMessage());
        }
        return apiResponse;
    }

    /**
     * 校验头寸信息是否正常
     */
    @PostMapping("/checkValidatorPosition")
    public ApiResponse checkValidatorPosition() {
        log.info("checkValidatorPosition start request");
        return strategyManager.valitPosition();
    }

    /**
     * 暂停、停止、重启策略
     */
    @PostMapping("/update")
    public ApiResponse updateStrategy(@RequestBody HedgeStrategyUpdateRequest request) {
        strategyManager.operateStrategy(request); //todo 只是使用策略ID吗
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setCode("0");
        apiResponse.setMessage("停止指令已下发");
        return apiResponse;
    }

    /**
     * 停止全部策略信息
     */
    @PostMapping("/stopAll")
    public ApiResponse stopAllStrategy(@RequestBody HedgeStrategyUpdateRequest request) {
        strategyManager.stopAllStrategyGraceFully();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setCode("0");
        apiResponse.setMessage("策略事例已停止");
        return apiResponse;
    }

    /**
     * 开启追单策略实例操作
     */
    @PostMapping("/startChaseStrategy")
    public ApiResponse startChaseStrategy(@RequestBody HedgeStrategyChaseRequest request) {
        log.info("chase start request: InstanceId=[{}], isChase=[{}], userName=[{}]",
                request.getInstanceId(), request.getIsChase(), request.getUserId());
        ApiResponse apiResponse = new ApiResponse();
        try {
            // 调用核心管理器进行加载和启动
            strategyManager.startChaseStrategy(request);
            apiResponse.setCode("0");
            apiResponse.setMessage("追单启动成功!");
        } catch (Exception e) {
            log.error("startChaseStrategy start failed!! instanceId:{} ", request.getInstanceId(), e);
            apiResponse.setCode("1");
            apiResponse.setMessage("追单启动失败!");
        }
        return apiResponse;
    }

    /**
     * 查询积存金事例信息
     */
    @PostMapping("/queryStrategyInstanceInfo")
    public QueryStrategyInstanceResponse queryStrategyInstanceInfo(@RequestBody QueryHedgeStrategyInstanceRequest request) {
        log.info("Receive query strategy request: InstanceId=[{}], userName=[{}]",
                request.getInstanceId(), request.getUserName());
        QueryStrategyInstanceResponse response = null;
        try {
            // 调用核心管理器进行加载和启动
            GoldStrategyBean goldStrategyBean = strategyManager.queryStrategyInstance(request);
            response = new QueryStrategyInstanceResponse(goldStrategyBean);
        } catch (Exception e) {
            log.error("startChaseStrategy start failed!! instanceId:{} ", request.getInstanceId(), e);
            response = new QueryStrategyInstanceResponse(null, "查询失败:" + e.getMessage());
        }
        return response;
    }

    /**
     * 开启撤单：
     * orderId：订单编号
     */
    @PostMapping("/cancleOrder")
    public ApiResponse cancleOrder(@RequestBody HedgeStrategyCancleRequest request) {
        log.info("cancle order start request: InstanceId=[{}], orderId=[{}], userName=[{}]",
                request.getInstanceId(), request.getOrderId(), request.getUserId());
        ApiResponse apiResponse = new ApiResponse();
        try {
            // 调用核心管理器进行加载和启动
            strategyManager.cancleOrder(request);
            apiResponse.setCode("0");
            apiResponse.setMessage("撤单成功!");
        } catch (Exception e) {
            log.error("cancle order start failed!! instanceId:{} ", request.getInstanceId(), e);
            apiResponse.setCode("1");
            apiResponse.setMessage("撤单失败!");
        }
        return apiResponse;
    }
}
