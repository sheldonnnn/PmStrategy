package com.cmbc.oms.domain.exposure.service;

import com.cmbc.oms.domain.order.model.ExecutionReport;
import org.springframework.stereotype.Service;

@Service
public class MgapHedgedFolderRouter implements IPositionFolderRouter{

    public static final String FOLDER_MGAP_HEDGE = "MgapHedge";
    
    @Override
    public boolean support(ExecutionReport executionReport) {
        //TODO:初步写死，后续改为基于业务标识来判断 businessType = "MgapHedge"
        return true;
    }

    @Override
    public String route(ExecutionReport executionReport) {
        
        //支持后续路由规则扩展
        return FOLDER_MGAP_HEDGE;
    }
}
