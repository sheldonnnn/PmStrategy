package com.cmbc.strategy.integration.impl;

import com.cmbc.oms.domain.exposure.dto.HedgePositionSummary;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.oms.domain.exposure.service.QuantPositionManager;
import com.cmbc.oms.domain.exposure.service.MgapClientPositionService;
import com.cmbc.strategy.integration.IPositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Author: Cly
 * @Date: 2026/02/28 19:34
 * @Description:
 */
@Service
public class PositionService implements IPositionService {

    @Autowired
    private MgapClientPositionService mgapClientPositionService;

    @Autowired
    private QuantPositionManager quantPositionManager;

    @Override
    public HedgePositionSummary getMgapPositionSummary() {
        return mgapClientPositionService.buildHedgePositionSummaryView();
    }

    @Override
    public PositionSnapshot getFolderPositionSummary(String folderId) {
        return quantPositionManager.getTotalPosition(folderId);
    }
}
