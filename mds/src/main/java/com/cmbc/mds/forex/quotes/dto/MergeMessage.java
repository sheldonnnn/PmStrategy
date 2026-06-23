package com.cmbc.mds.forex.quotes.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MergeMessage extends BaseMsg {
    private String provider;
    private String symbol;

    private Depth data;

    // 标识是否为看门狗产生的断线清理事件
    private boolean disconnectEvent;
    private String disconnectedSource;

    public MergeMessage(String provider, String symbol, Depth data) {
        this.provider = provider;
        this.symbol = symbol;
        this.data = data;
    }

    @Override public String toString() { return "MergeMsg(data=" + data + ", disconnectEvent=" + disconnectEvent + ", disconnectedSource=" + disconnectedSource + ")"; }
}
