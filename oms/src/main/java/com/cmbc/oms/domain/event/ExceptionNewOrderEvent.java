package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;
import lombok.Data;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/5/15
 * @time 9:32
 * @description 异常订单事件
 */
@Data
@EventField(name = "com.finesys.order.ExceptionNewOrder")
public class ExceptionNewOrderEvent {
    @EventField(name = "orderId", order = 1)
    private String orderId;//订单编号
    @EventField(name = "localOrderNo", order = 2)
    private String localOrderNo;//本地订单编号
    @EventField(name = "strategyId", order = 3)
    private String strategyId;//策略编号
    @EventField(name = "symbol", order = 4)
    private String symbol;//货币对
    @EventField(name = "price", order = 5)
    private double price;//委托价格
    @EventField(name = "newOrderQty", order = 6)
    private String newOrderQty;//newOrder扩展字段中的下单量字段：“38”，贵金属是quantity
    @EventField(name = "side", order = 7)
    private String side;//买卖方向
    @EventField(name = "type", order = 8)
    private String type;//下单类型  "MARKET", "LIMIT", "STOP"
    @EventField(name = "serviceId", order = 9)
    private String serviceId;//UBS-FIX,JPMC-FIX,COBA-FIX
    @EventField(name = "marketId", order = 10)
    private String marketId;//JPMC_TRADING,UBS_TRADING,COBA_TRADING
    @EventField(name = "createTime", order = 11)
    private String createTime;//登记时间
    @EventField(name = "overTime", order = 12)
    private String overTime;//超时时间
    @EventField(name = "dealType", order = 13)
    private String dealType;//处理状态。0：未处理，1：人工干预-拒单，2：人工干预-成交，3 人工撤单,4：系统恢复处理
    @EventField(name = "domesticType", order = 14)
    private String domesticType;//境内外订单类型
    @EventField(name = "extraParams", order = 15)
    private Map<String, String> extraParams;
}
