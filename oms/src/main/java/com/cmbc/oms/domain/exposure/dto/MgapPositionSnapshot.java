package com.cmbc.oms.domain.exposure.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 积存金头寸快照模型
 */
@Data
public class MgapPositionSnapshot implements Serializable {
    private String symbol;

    private String currency;

    private BigDecimal qty;

    private BigDecimal amt;

    private BigDecimal mktPrice;    // 行情价格

    private BigDecimal price;       // 成本价格

    private BigDecimal unrealizedPL;

    private String positionTime;

    private String priceTime;

}
