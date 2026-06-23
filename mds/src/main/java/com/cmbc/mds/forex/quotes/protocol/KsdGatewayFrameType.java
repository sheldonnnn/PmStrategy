package com.cmbc.mds.forex.quotes.protocol;

public enum KsdGatewayFrameType {

    // 行情帧：携带KSD盘口行情，FxQuote收到后进入报价处理链路。
    QUOTE(1),
    // 心跳帧：携带Gateway连接、登录、订阅等运行状态，供FxQuote判断链路健康。
    HEARTBEAT(2);

    private final int code;

    KsdGatewayFrameType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static KsdGatewayFrameType fromCode(int code) {
        for (KsdGatewayFrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
