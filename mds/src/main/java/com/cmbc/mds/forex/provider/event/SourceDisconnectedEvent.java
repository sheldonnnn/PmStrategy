package com.cmbc.mds.forex.provider.event;

import org.springframework.context.ApplicationEvent;

/**
 * 价源断线事件。
 */
public class SourceDisconnectedEvent extends ApplicationEvent {

    private final String sourceName;

    public SourceDisconnectedEvent(Object source, String sourceName) {
        super(source);
        this.sourceName = sourceName;
    }

    /**
     * 返回已标准化的价源名称。
     */
    public String getSourceName() {
        return sourceName;
    }
}
