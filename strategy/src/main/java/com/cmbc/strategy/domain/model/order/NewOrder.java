package com.cmbc.strategy.domain.model.order;

import com.cmbc.strategy.constant.OrderType;
import com.cmbc.strategy.constant.Side;
import com.cmbc.strategy.constant.TimeInForce;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class NewOrder {

    private BigDecimal orderQty;
    private OrderType type;
    private String symbol;
    private BigDecimal price;
    private TimeInForce timeInForce;
    private Side side;
    private String orderId;


}
