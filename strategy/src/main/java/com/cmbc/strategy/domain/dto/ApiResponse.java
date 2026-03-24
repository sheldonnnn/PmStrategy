package com.cmbc.strategy.domain.dto;

public enum ApiResponse {

    success(),

    /**
     * 1: 平盘条件验证中 (Monitoring)
     * 含义：[运行态] 策略正在监控头寸，等待触发阈值。
     */
    error();

}
