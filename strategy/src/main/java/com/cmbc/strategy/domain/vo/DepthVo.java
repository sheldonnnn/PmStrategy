package com.cmbc.strategy.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepthVo {

    private Integer level;
    // 合约品种
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal price;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal levelQty;

}
