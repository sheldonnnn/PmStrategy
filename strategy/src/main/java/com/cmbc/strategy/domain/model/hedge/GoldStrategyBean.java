package com.cmbc.strategy.domain.model.hedge;

import com.cmbc.strategy.domain.vo.DepthVo;
import com.cmbc.strategy.domain.vo.HedgePositionVo;
import com.cmbc.strategy.domain.vo.NewOrderVo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 查询积存金积存金策略模版实体
 *
 * @Author: zm
 * @Date: Created on 20190808 14:56.
 */
@Data
public class GoldStrategyBean {

    // 运行事例
    private String instanceId;

    private String userName;
    private String status; // 策略运行状态
    private String message; //描述
    private String symbol; // 平盘合约品种
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal noCumQty; // 未成交量
    private String errorMessage;
    private Integer noOrderNumber;// 未成交订单笔数
    private BigDecimal clientPrice;// 积存金对客报价
    private String clientPriceTime;   // 报价时间
    private BigDecimal fxSymbol; // 实时汇率
    private String fxSymbolTime; // 汇率更新时间

    // 汇总信息
    private GoldHedgeStrategyTotalBean goldHedgeStrategyTotalBean;
    private List<DepthVo> depthList;
    // 具体合约信息
    private List<GoldHedgeStrategyBean> list;

    private List<HedgePositionVo> hedgePositionList;
    // 委托明细
    private List<NewOrderVo> orderList;

}
