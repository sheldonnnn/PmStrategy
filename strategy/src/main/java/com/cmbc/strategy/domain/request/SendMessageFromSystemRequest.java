package com.cmbc.strategy.domain.request;

import lombok.Data;

/**
 * 短信通知
 */
@Data
public class SendMessageFromSystemRequest {

    private CommonHeader commonHeader;

    private SendMessageFromSystemBody body;

}
