package com.cmbc.strategy.domain.model.market;

import lombok.Data;

/**
 * @Author: Cly
 * @Date: 2026/03/02  15:38
 * @Description: 策略订阅类
 */
@Data
public class SubscribeRequest {
    private String symbol;
    private String exchId; //交易所
    private String counterParty;
}
