package com.cmbc.oms.domain.order.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class NewOrder {

    private String orderId;
    private String symbol;

    private BigDecimal price;
    private String side;

    private String type;
    private BigDecimal quantity;
    private BigDecimal quantytyLeft;
    private BigDecimal dealAmount;
    private BigDecimal dealAvgPrice;

    private String domesticType;
    private BigDecimal exchange;




}
