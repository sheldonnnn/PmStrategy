package com.cmbc.oms.domain.exposure.dto;

import com.cmbc.oms.domain.exposure.model.PositionSummary;
import com.cmbc.oms.domain.exposure.vo.PositionVo;
import lombok.Data;

import java.util.List;

@Data
public class PositionDataResponse {
    private List<PositionVo> mgapPosition;// 积存金数据
    private List<PositionVo> hedgedPosition; // 量化平盘头寸
    private PositionSummary positionSummary; // 汇总数据

    private boolean isConnected;

}
