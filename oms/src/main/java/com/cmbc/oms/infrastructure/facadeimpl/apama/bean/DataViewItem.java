package com.cmbc.oms.infrastructure.facadeimpl.apama.bean;

import java.io.Serializable;
import java.util.Map;

/**
 * @Author: Cly
 * @Date: 2026/01/23  12:14
 * @Description:
 */
public class DataViewItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private String displayName;
    private String rowId;
    private Map<String, Object> row;
    private DataViewEventType type;

    public DataViewItem() { this.type = DataViewEventType.ADD_OR_UPDATE; }

    public String getRowId() { return this.rowId; }

    public void setRowId(String rowId) { this.rowId = rowId; }

    public Map<String, Object> getRow() { return this.row; }

    public void setRow(Map<String, Object> row) { this.row = row; }

    public String getDisplayName() { return this.displayName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public DataViewEventType getType() { return this.type; }

    public void setType(DataViewEventType type) { this.type = type; }
}
