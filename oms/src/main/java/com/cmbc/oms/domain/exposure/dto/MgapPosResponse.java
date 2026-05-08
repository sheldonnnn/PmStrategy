package com.cmbc.oms.domain.exposure.dto;

import lombok.Data;

@Data
public class MgapPosResponse {

    private String returnCode;
    private Map<String,MgapPositionSnapShot> totalAll;

}
