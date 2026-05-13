package com.cmbc.strategy.integration;

import com.cmbc.oms.domain.exposure.dto.StrategyPosition;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;

import java.math.BigDecimal;

public interface IPositionService {

    public StrategyPosition getMgapPositionSummary(); // 查积存金头寸
    public PositionSnapshot getFolderPositionSummary(String folderId); // 查已平盘头寸

}
