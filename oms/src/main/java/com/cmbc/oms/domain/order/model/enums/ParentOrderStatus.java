package com.cmbc.oms.domain.order.model.enums;

/**
 * @author chendaqian
 * @date 2026/5/25
 * @time 15:46
 * @description 母单状态字典
 */
public enum ParentOrderStatus {
    CREATED("-1", "创建", false),
    PARTIAL_FILL("2", "部分成交", false),
    FILLED("3", "全部成交", true),
    CANCELLED("4", "全部撤销/拒单", true),
    PARTIAL_CANCELLED("5", "部分成交+部分撤销/拒单", true)
    ;

    private final String statusCode;
    private final String statusName;
    private final boolean isFinal;

    ParentOrderStatus(String statusCode, String statusName, boolean isFinal) {
        this.statusCode = statusCode;
        this.statusName = statusName;
        this.isFinal = isFinal;
    }

    public String getStatusCode() { return statusCode; }

    public boolean isFinal() { return isFinal; }

    public String getStatusName() { return statusName; }

}
