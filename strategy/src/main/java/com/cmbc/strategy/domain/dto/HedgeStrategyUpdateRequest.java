package com.cmbc.strategy.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class HedgeStrategyUpdateRequest {

    private String instanceId; // 此次运行的唯一Trace ID（如 UUID）

    /**
     * 流水号
     */
    private String transId;

    /**
     * 操作类型 0启动
     * ,1停止
     * ,2策略暂停
     * * 4策略恢复
     * @return
     */
    private String oprType;

    /**
     * 用户名
     * @return
     */
    private String userName;

}
