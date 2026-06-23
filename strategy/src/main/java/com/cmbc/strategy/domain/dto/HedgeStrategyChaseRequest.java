package com.cmbc.strategy.domain.dto;

import lombok.Data;

/**
 * 开启追单请求
 */
@Data
public class HedgeStrategyChaseRequest {

    private String instanceId; // 实例ID

    private String userId; // 用户ID
    private String isChase; // 是否允许追单 1: 允许 0: 不允许

}
