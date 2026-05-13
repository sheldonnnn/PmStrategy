package com.cmbc.oms.domain.facade;

import com.cmbc.oms.domain.order.model.ExecutionReport;

public interface ExecutionReportListener {

    void onAck(ExecutionReport executionReport);
    void onReject(ExecutionReport executionReport);
    void onMatch(ExecutionReport executionReport);
    void onCancel(ExecutionReport executionReport);


}
