package com.cmbc.strategy.integration.impl;

import com.cmbc.oms.domain.exposure.dto.StrategyPosition;
import com.cmbc.oms.domain.exposure.model.PositionSnapshot;
import com.cmbc.oms.domain.exposure.service.ExposureManage;
import com.cmbc.oms.domain.exposure.service.MgapClientPositionManage;
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
    private MgapClientPositionManage mgapClientPositionManage;

    @Autowired
    private ExposureManage exposureManage;

    @Override
    public StrategyPosition getMgapPositionSummary() {
        return mgapClientPositionManage.getMgapPositionSummaryCache();
    }

    @Override
    public PositionSnapshot getFolderPositionSummary(String folderId) {
        return exposureManage.getTotalPosition(folderId);
    }
}
