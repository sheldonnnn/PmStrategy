package com.cmbc.oms.controller.dto;

/**
 * @author chendaqian
 * @date 2026/2/11
 * @time 14:56
 * @description 策略下单响应结果
 */
public class StrategyOrderRes {
    private String type;
    private String code;
    private String message;
    private String data;

    public String getType() { return type; }

    public static StrategyOrderRes success() {
        return new StrategyOrderRes().setCode("000000")
                .setType("S").setMessage("发单成功");
    }

    public static StrategyOrderRes fail(String errorMessage) {
        return new StrategyOrderRes().setCode("E00001")
                .setType("F").setMessage(errorMessage);
    }

    public StrategyOrderRes setType(String type) {
        this.type = type;
        return this;
    }

    public String getCode() { return code; }

    public StrategyOrderRes setCode(String code) {
        this.code = code;
        return this;
    }

    public String getMessage() { return message; }

    public StrategyOrderRes setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getData() { return data; }

    public StrategyOrderRes setData(String data) {
        this.data = data;
        return this;
    }
}
