package com.cmbc.oms.domain.exposure.dto;

import com.cmbc.oms.controller.dto.RCode;
import com.cmbc.oms.domain.exposure.vo.SymbolPositionVo;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class MgapPositionResponse implements Serializable {
    private RCode returnCode;
    private List<SymbolPositionVo> positionList;

    public MgapPositionResponse(String message) {
        RCode returnCode = new RCode();
        returnCode.setCode("EEEEEEE");
        returnCode.setDomain("511");
        returnCode.setMessage(message);
        returnCode.setType("E");
        this.returnCode = returnCode;
        this.positionList = new ArrayList<>();
    }

    public MgapPositionResponse(List<SymbolPositionVo> inv) {
        RCode returnCode = new RCode();
        returnCode.setCode("AAAAAA");
        returnCode.setDomain("511");
        returnCode.setMessage("success");
        returnCode.setType("S");
        this.returnCode = returnCode;
        this.positionList = inv;
    }
}
