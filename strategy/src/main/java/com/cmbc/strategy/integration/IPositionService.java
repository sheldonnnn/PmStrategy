package com.cmbc.strategy.integration;

import java.math.BigDecimal;

public interface IPositionService {

    public BigDecimal getClientPosition(); // 查积存金头寸
    public BigDecimal getHedgedPosition(); // 查已平盘头寸

    public BigDecimal getActiveExposure();

}
