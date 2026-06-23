package com.cmbc.strategy.domain.response;

import com.cmbc.strategy.domain.model.hedge.GoldStrategyBean;

public class QueryStrategyInstanceResponse {
    private GoldStrategyBean data;
    private boolean success;
    private boolean error;
    private String code;
    private String message;

    // 默认构造函数
    public QueryStrategyInstanceResponse() {
    }

    // 带数据的构造函数
    public QueryStrategyInstanceResponse(GoldStrategyBean data) {
        this.data = data;
        this.success = true;
        this.error = true;
        this.message = "success";
        this.code = "0000";
    }

    // 带错误信息的构造函数
    public QueryStrategyInstanceResponse(GoldStrategyBean data, String message) {
        this.data = data;
        this.success = true;
        this.error = false;
        this.code = "9999";
        this.message = message;
    }

    // 带完整参数的构造函数
    public QueryStrategyInstanceResponse(GoldStrategyBean data, String code, String message) {
        this.data = data;
        this.success = true;
        this.message = message;
        this.code = code;
    }
}
