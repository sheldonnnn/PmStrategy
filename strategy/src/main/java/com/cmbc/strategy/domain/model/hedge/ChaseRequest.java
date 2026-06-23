package com.cmbc.strategy.domain.model.hedge;

import lombok.Data;

/**
 * 追单告警信息
 *
 * @Author: zm
 * @Date: Created on 20190808 14:56.
 */
@Data
public class ChaseRequest {

    // 运行事例
    private String instanceId;

    private String userName;
    private String message;

}
