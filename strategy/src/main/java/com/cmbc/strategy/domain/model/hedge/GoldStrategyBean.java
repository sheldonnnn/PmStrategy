package com.cmbc.strategy.domain.model.hedge;

import lombok.Data;
import java.util.List;

/**
 * 查询积存金积存金策略模板实体
 * * @Author: cly
 * @Date: Created on 20190808 14:56.
 */
@Data
public class GoldStrategyBean {

    // 运行事例
    private String instanceId;
    private String userName;
    private String status; // 策略运行状态
    private String message; // 描述

    // 汇总信息
    private GoldHedgeStrategyTotalBean goldHedgeStrategyTotalBean;
    private List<DepthVo> depthList;

    // 具体合约信息
    private List<GoldHedgeStrategyBean> list;

    private List<HedgePositionVo> hedgePositionList;

    // 委托明细
    private List<NewOrderVo> orderList;

}
