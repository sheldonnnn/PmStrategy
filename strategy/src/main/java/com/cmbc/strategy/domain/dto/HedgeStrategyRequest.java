package com.cmbc.strategy.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class HedgeStrategyRequest {

    @NotBlank(message = "策略实例ID不能为空")
    private String instanceId;   // 此次运行的唯一Trace ID (如 UUID)

    @NotNull(message = "基础配置ID不能为空")
    private String baseConfigId;   // 对应 GOLD_STRATEGY_BASE_CONFIG 表主键

    @NotNull(message = "合约规则组ID不能为空")
    private String symbolConfigId; // 对应 GOLD_STRATEGY_TIME_RULES 表的关联Group ID


}
