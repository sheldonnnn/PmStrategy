package com.cmbc.strategy.domain.dto;

import lombok.Data;

/**
 * 策略撤单:订单维度
 */
@Data
public class HedgeStrategyCancleRequest {

    private String instanceId; // 实例ID

    private String userId; // 用户ID
    private String orderId; // 订单ID

}
