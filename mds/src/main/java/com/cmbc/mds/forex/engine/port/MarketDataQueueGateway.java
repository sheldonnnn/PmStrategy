package com.cmbc.mds.forex.engine.port;

import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.MergeMessage;

/**
 * 行情处理队列写入端口。
 */
public interface MarketDataQueueGateway {

    /**
     * 将原始行情写入清洗队列；未注册通道的数据会被丢弃。
     */
    void pushToClean(String cleanId, Depth depth);

    /**
     * 将清洗后行情或聚合控制事件写入聚合队列；未注册通道的数据会被丢弃。
     */
    void pushToMerge(String mergeId, MergeMessage message);
}
