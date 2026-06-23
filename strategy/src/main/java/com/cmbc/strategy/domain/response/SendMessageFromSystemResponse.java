package com.cmbc.strategy.domain.response;

import com.cmbc.oms.controller.dto.RCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class SendMessageFromSystemResponse implements Serializable {
    private RCode returnCode;

}
