package com.cmbc.oms.domain.exposure.dto;

import com.cmbc.oms.controller.dto.RCode;
import com.cmbc.oms.domain.exposure.model.MgapPositionSnapshot;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class MgapPosResponse implements Serializable {
    private RCode returnCode;
    private Map<String, MgapPositionSnapshot> totalAll;

}
