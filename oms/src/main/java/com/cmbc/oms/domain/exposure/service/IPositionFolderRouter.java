package com.cmbc.oms.domain.exposure.service;

import com.cmbc.oms.domain.order.model.ExecutionReport;

public interface IPositionFolderRouter {
    
    //基于风控路由规则判断是否匹配此订单
    public boolean support(ExecutionReport executionReport);
    
    //根据订单事件返回对应folderID
    public String route(ExecutionReport executionReport);
    
}
