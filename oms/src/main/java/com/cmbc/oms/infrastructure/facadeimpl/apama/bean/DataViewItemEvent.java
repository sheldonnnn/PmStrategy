package com.cmbc.oms.infrastructure.facadeimpl.apama.bean;

import org.springframework.context.ApplicationEvent;

/**
 * @Author: Cly
 * @Date: 2026/01/23  12:16
 * @Description:
 */
public class DataViewItemEvent extends ApplicationEvent {
    private DataViewItem item;

    public DataViewItemEvent(DataViewItem item) {
        super(item.getDisplayName());
        this.item = item;
    }

    public DataViewItem getItem() { return this.item; }

    public void setItem(DataViewItem item) { this.item = item; }
}
