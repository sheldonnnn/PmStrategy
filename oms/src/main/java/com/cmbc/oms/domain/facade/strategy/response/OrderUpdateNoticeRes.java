package com.cmbc.oms.domain.facade.strategy.response;

/**
 * @author chendaqian
 * @date 2026/2/5
 * @time 9:31
 * @description
 */
public class OrderUpdateNoticeRes {
    private String type;
    private String code;
    private String message;

    public String getType() { return type; }

    public OrderUpdateNoticeRes setType(String type) {
        this.type = type;
        return this;
    }

    public String getCode() { return code; }

    public OrderUpdateNoticeRes setCode(String code) {
        this.code = code;
        return this;
    }

    public String getMessage() { return message; }

    public OrderUpdateNoticeRes setMessage(String message) {
        this.message = message;
        return this;
    }
}
