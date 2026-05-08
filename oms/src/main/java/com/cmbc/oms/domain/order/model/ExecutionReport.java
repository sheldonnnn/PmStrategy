package com.cmbc.oms.domain.order.model;


import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

//交易下行之行回报
@Data
    public class ExecutionReport implements Serializable {

        private String orderId;            // OMS自身生成唯一订单ID
        private String exchOrderId;        // 交易所返回订单ID
        private String strategyOrderId;    // 策略订单ID（母单）
        private String execId;             // 成交ID
        private String side;               // 方向
        private String symbol;             // 合约代码
        private String currency;           // 币种
        private String ordType;            // 订单类型
        private BigDecimal orderQty;       // 委托数量
        private BigDecimal price;          // 委托价格
        private BigDecimal lastPrice;      // 成交价格
        private BigDecimal lastQty;        // 成交数量
        private BigDecimal lastAmt;        // 成交额
        private BigDecimal avgPx;          // 成交均价
        private BigDecimal cumQty;         // 累计成交量
        private BigDecimal cumAmt;         // 累计成交额
        private BigDecimal leavesQty;      // 剩余可用数量
        private BigDecimal canceledQty;    // 撤单数量
        private BigDecimal unit;           // 合约乘数
        private String businessType;       // 业务类型标识 (DB列: BUSINESS_TYPE)
        private String strategyId;         // 策略ID
        private String instanceId;         // 策略实例ID
        private String userName;           // 交易员
        private String tagCode;            // 交易标签
        private String tagName;
        private String timeInForce;        // 订单时效类型
        private String exchId;             // 交易场所/通道
        private String counterParty;       // 交易对手方
        private String marketSegmentId;    // 市场分类
        private String securityType;       // 合约类型
        private String productType;        // 系统产品类型
        private String memberId;           // 会员号
        private String clientId;           // 客户号
        private String inventoryType;      // 现货标识
        private String domesticType;       // 境内境外标识
        private String openFlag;           // 开平方向
        private String shFlag;             // 上金所投机保值标志
        private LocalDateTime orderTime;   // 委托时间（业务时间）
        private LocalDateTime matchTime;   // 成交时间
        private String matchDate;          // 成交日期
        private String status;



}
