package com.cmbc.oms.domain.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 贵金属订单实体类 (PM_ORDER)
 */
@Data
public class PmOrder {
    private String orderId;            // OMS自身生成唯一订单ID (主键)
    private String exchOrderId;        // 交易所返回订单ID
    private String strategyOrderId;    // 策略订单ID
    private String side;               // 方向
    private String symbol;             // 合约代码
    private String currency;           // 币种
    private String ordType;            // 订单类型
    private BigDecimal orderQty;       // 委托数量
    private BigDecimal price;          // 委托价格
    private BigDecimal avgPx;          // 成交均价
    private BigDecimal cumQty;         // 累计成交量
    private BigDecimal canceledQty;    // 撤单数量
    private BigDecimal cumAmt;         // 累计成交额
    private BigDecimal unit;           // 合约乘数     // 结算日期
    private String businessType;       // 业务类型标识 (DB列: BYSUBESS_TYPE)
    private String strategyId;         // 策略ID
    private String instanceId;         // 策略实例ID
    private String userName;           // 交易员
    private String tag;                // 交易标签
    private String timeInForce;        // 订单时效类型
    private String exchId;            // 交易场所/通道
    private String counterparty;     // 交易对手方
    private String marketSegmentId;    // 市场分类
    private String securityType;       // 合约类型
    private String productType;        // 系统产品类型
    private String memberId;           // 会员号
    private String clientId;           // 客户号
    private String inventoryType;      // 现货标识
    private String domesticType;       // 境内境外标识
    private String openFlag;           // 开平方向
    private String shFlag;             // 上金所投保标志
    private LocalDateTime orderTime;   // 委托时间 (业务时间)
    private String orderDate;          // 订单日期
    private String status;             // 状态
    private String statusMsg;          // 状态信息
    private String extra;              // 额外参数
    private LocalDateTime createdTime; // 创建时间
    private LocalDateTime updatedTime; // 更新时间
}