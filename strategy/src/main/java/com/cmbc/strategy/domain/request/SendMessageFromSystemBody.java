package com.cmbc.strategy.domain.request;

import lombok.Data;

/**
 * 消息推送报文体
 */
@Data
public class SendMessageFromSystemBody {
    private String applyId;
    private String channels;
    private String receivers;
    private String receiversType;
    private String params;
    private String transmitParams;

}
