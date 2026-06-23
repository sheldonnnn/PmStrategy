package com.cmbc.strategy.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 策略委托明细
 *
 * @Author: zm
 * @Date: Created on 20190808 14:56.
 */
@Data
public class NewOrderVo {

    // 合约品种
    private String symbol;
    private String side;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal orderQty;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal price; // 委托价格
    private String status;
    private Integer chaseNumber;
    // 订单编号
    private String orderId;
    private String orderTime; //订单委托时间

}
