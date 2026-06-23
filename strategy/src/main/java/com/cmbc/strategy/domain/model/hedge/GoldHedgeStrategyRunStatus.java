package com.cmbc.strategy.domain.model.hedge;

import lombok.Data;

/**
 * 查询积存金积存金策略模版实体
 *
 * @Author: zm
 * @Date: Created on 20190808 14:56.
 */
@Data
public class GoldHedgeStrategyRunStatus {
    // 运行事例
    private String instanceId;

    private String userName;
    private String status;
    private String statusMsg; // 状态描述
    private String message;// 策略异常信息返回
}
