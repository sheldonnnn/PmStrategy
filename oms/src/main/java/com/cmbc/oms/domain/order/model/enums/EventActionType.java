package com.cmbc.oms.domain.order.model.enums;

/**
 * @author chendaqian
 * @date 2026/6/3
 * @time 16:56
 * @description 下行事件类型枚举
 */
public enum EventActionType {
    ACK, // 委托确认
    MATCH, // 部分成交/全部成交
    CANCEL, // 撤销成功
    REJECT, // 交易所拒单
    IN_REJECT, // 系统/风控拒单
    CANCEL_REJECT; // 撤销拒单

}
