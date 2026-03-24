package com.cmbc.oms.cash;

import lombok.Getter;

/**
 * 特权内部事件：头寸重算完成后的影子字典交接事件。
 * 继承自 OrderUpdate 以便能够扔进同一个单线程的无锁消费队列中。
 */
@Getter
public class RecalculateCmdEvent extends OrderUpdate {

    // 独立线程在后台算好的纯洁的新头寸组（影子字典）
    private final FolderPosition shadowPosition;

    // 防止因为新老交集而重复叠加的最高水位线（最新流水号）
    private final long maxProcessedMatchNo;

    public RecalculateCmdEvent(FolderPosition shadowPosition, long maxProcessedMatchNo) {
        this.shadowPosition = shadowPosition;
        this.maxProcessedMatchNo = maxProcessedMatchNo;
    }
}
