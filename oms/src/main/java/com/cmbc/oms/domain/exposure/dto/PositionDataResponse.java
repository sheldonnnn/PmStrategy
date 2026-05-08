package com.cmbc.oms.domain.exposure.dto;

import com.cmbc.oms.domain.exposure.model.PositionSummary;
import com.cmbc.oms.domain.exposure.vo.PositionVo;
import lombok.Data;

import java.util.List;

@Data
public class PositionDataResponse {

    private List<PositionVo> mgapPosition;
    private List<PositionVo> hedgedPosition;
    private PositionSummary positionSummary;

    private boolean isConnected;

}
