package com.cmbc.oms.controller.dto;

import com.cmbc.oms.domain.order.model.NewOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
@Data
public class StrategyOrder {

    private String orderId;

    //策略实例ID
    private String instanceId;

    //用户ID
    private String userId;

    private String traderNo;

    //品种
    private String symbol;

    //买卖方向
    private String side;

    // 境内外标识
    private String domesticType;

    //委托数量
    private BigDecimal qty;

    //委托价格
    private BigDecimal price;

    /**下单方式.1:vwap 下单; 2:点击价格下单*/
    private String orderForm;

    //分组信息
    private Integer groupCount;

    //过期时间
    private BigDecimal timeOut;

    //订单类型
    private String orderType;

    private String memberId;

    private String clientId;

    private String businessType;

    private String tagCode;

    private String tagName;

    private String exchCode;

    private String counterParty;

    private String currency;

    private String offsetFlag; // 先平后开/只开不平

    //成交数量
    private BigDecimal cumQty;

    //剩余数量
     private BigDecimal leavesQty;
     private BigDecimal avgPrice;
     private BigDecimal dealRate;
     private BigDecimal cumAmount;

     private boolean isEnd;
     private List<NewOrder> newOrderList;


}
