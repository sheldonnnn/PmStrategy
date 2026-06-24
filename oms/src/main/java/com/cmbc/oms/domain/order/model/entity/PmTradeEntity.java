package com.cmbc.oms.domain.order.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PmTradeEntity {

    private String execId; // 成交ID (主键)
    private String orderId; // 订单ID
    private String exchOrderId; // 交易所订单ID
    private String strategyOrderId; // 策略订单ID
    private String side; // 方向
    private String symbol; // 合约
    private String currency; // 币种
    private String ordType; // 订单类型
    private BigDecimal lastQty; // 成交数量
    private BigDecimal orderQty; // 委托订单成交量
    private BigDecimal lastPx; // 成交价格
    private BigDecimal lastAmt; // 成交额
    private String businessType; // 业务类型标识 (DB列: BYSUBESS_TYPE)
    private String strategyId; // 策略ID
    private String instanceId; // 策略实例ID
    private String userName; // 交易员
    private String tagCode; // 标签
    private String tagName;
    private String timeInForce; // 订单时效类型
    private String counterParty; // 对手方
    private String exchId; // 市场标识
    private String matchTime; // 成交时间
    private String matchDate; // 成交日期
    private String memberId; // 会员号
    private String clientId; // 客户号
    private String securityType; // 合约类型
    private String inventoryType; // 现货标识
    private String domesticType; // 境内境外标识
    private String openFlag; // 开平方向
    private String shFlag; // 投保标志
    private String tradePurpose;
    private String extra; // 额外参数
    private LocalDateTime createdTime; // 创建时间
    private LocalDateTime updatedTime; // 更新时间

    private String traderNo; // dimple用户

}
