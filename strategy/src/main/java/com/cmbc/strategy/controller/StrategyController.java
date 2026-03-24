package com.cmbc.strategy.controller;

import com.cmbc.strategy.domain.dto.ApiResponse;
import com.cmbc.strategy.domain.dto.HedgeStrategyRequest;
import com.cmbc.strategy.service.HedgeStrategyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/strategy")
@Slf4j
public class StrategyController {


    @Autowired
    private HedgeStrategyManager strategyManager;

    /**
     * 启动平盘策略实例
     */
    @PostMapping("/start")
    public ApiResponse<String> startStrategy(@RequestBody HedgeStrategyRequest request) {
        log.info("Receive start request: InstanceId=[{}], BaseConfig=[{}], ContractConfig=[{}]",
                request.getInstanceId(), request.getBaseConfigId(), request.getSymbolConfigId());

        try {
            // 调用核心管理器进行加载和启动
            strategyManager.startStrategy(request);
            return ApiResponse.success("启动成功");
        } catch (Exception e) {
            log.error("启动失败: " + request.getInstanceId(), e);
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止策略
     */
    @PostMapping("/stop/{instanceId}")
    public ApiResponse<String> stopStrategy(@PathVariable String instanceId) {
        strategyManager.stopStrategy(instanceId);
        return ApiResponse.success("停止指令已下发");
    }


}