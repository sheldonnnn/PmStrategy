package com.cmbc.strategy.integration;

import com.cmbc.oms.domain.exposure.model.HedgePositionSummary;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;

public interface IPositionService {

    public PositionSnapshot getFolderPositionSummary(String folderId); // 查积folder维度汇总头寸
    public HedgePositionSummary getMgapPositionSummary();
}
