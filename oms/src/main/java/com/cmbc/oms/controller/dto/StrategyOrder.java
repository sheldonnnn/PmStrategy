package com.cmbc.oms.controller.dto;

import com.cmbc.oms.domain.order.model.entity.NewOrder;
import com.cmbc.oms.domain.order.model.enums.ParentOrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author chendaqian
 * @date 2026/2/5
 * @time 10:31
 * @description 策略订单（母单）
 */
@Data
public class StrategyOrder {
    //请求订单ID
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
    /**下单方式,1:vwap下单, 2:点击价格下单*/
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

    //执行数量
    private BigDecimal cumQty;
    //剩余数量
    private BigDecimal leavesQty;
    //成交比例
    private BigDecimal dealRate;
    //成交金额
    private BigDecimal cumAmount;
    // 净敞口头寸
    private BigDecimal netPosition;
    //成交均价
    private BigDecimal avgPrice;
    private volatile int totalChildCount;
    private final AtomicInteger finishedChildCount = new AtomicInteger(0);
    private volatile String status;

    /**订单执行完成标志：当撤单失败时对订单进行结束*/
    private boolean isEnd;

    //子单list，对应原来的 NewOrder
    private List<NewOrder> newOrderList;

    public StrategyOrder() {
        // 母单成交数据初始化
        this.cumQty = BigDecimal.ZERO;
        this.leavesQty = BigDecimal.ZERO;
        this.dealRate = BigDecimal.ZERO;
        this.cumAmount = BigDecimal.ZERO;
        this.avgPrice = BigDecimal.ZERO;
        this.netPosition = BigDecimal.ZERO;
        this.totalChildCount = 0;
        this.status = ParentOrderStatus.CREATED.getStatusCode();
        this.isEnd = false;
    }

    // 判断是否全部完成
    public boolean isAllFinished() { return finishedChildCount.get() >= totalChildCount; }

    // 删除母单缓存前，需要将子单list清空，断开引用，便于GC回收
    public void clearListData(){
        if(this.newOrderList != null){
            this.newOrderList.clear();
            this.newOrderList = null;
        }
        this.isEnd = true;
    }
}
