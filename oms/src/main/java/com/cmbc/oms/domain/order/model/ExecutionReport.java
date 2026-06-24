package com.cmbc.oms.domain.order.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author chendaqian
 * @date 2026/2/26
 * @time 14:25
 * @description 订单更新报文实体
 */
@Data
public class ExecutionReport implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String orderId; // OMS自身生成唯一订单ID
    private String exchOrderId; // 交易所返回订单ID/系统订单id
    private String strategyOrderId; // 策略订单ID（母单）
    private String execId; // 成交ID
    private String side; // 方向
    private String symbol; // 合约代码
    private String currency; // 币种
    private String ordType; // 订单类型
    private BigDecimal orderQty; // 委托数量
    private BigDecimal price; // 委托价格
    private BigDecimal lastPrice; // 成交价格
    private BigDecimal lastQty; // 成交数量
    private BigDecimal avgPx; // 成交均价
    private BigDecimal cumQty; // 累计成交量
    private BigDecimal cumAmt; // 累计成交额
    private BigDecimal leavesQty; // 剩余可用数量
    private BigDecimal canceledQty; // 撤单数量
    private BigDecimal unit; // 合约乘数
    private String businessType; // 业务类型标识 (DB列：BYSUBESS_TYPE)
    private String strategyId; // 策略ID
    private String instanceId; // 策略实例ID
    private String userName; // 交易员
    private String tagCode; // 交易标签
    private String tagName;
    private String timeInForce; // 订单时效类型
    private String exchId; // 交易场所/通道
    private String counterParty; // 交易对手方
    private String marketSegmentId; // 市场分类
    private String securityType; // 合约类型
    private String productType; // 系统产品类型
    private String memberId; // 会员号
    private String clientId; // 客户号
    private String inventoryType; // 现货标识
    private String domesticType; // 境内外标识
    private String openFlag; // 开平方向
    private String shFlag; // 上金所投保标志
    private LocalDateTime orderTime; // 委托时间（业务时间）
    private String matchTime; // 成交时间
    private String matchDate; // 成交日期
    private String orderDate; // 订单日期
    private String status; // 订单最新待更新状态
    private String apamaStatus; // 记录apama原始状态码 errorId
    private String oldStatus; // 状态机流转前的旧状态
    private String actionType; // 执行回报类型 ACK, MATCH, CANCEL, REJECT, IN_REJECT, CANCEL_REJECT
    private String statusMsg; // 状态信息
    private String systemId; // 
    private String tradePurpose; //交易目的
    private String traderNo; 
    private String varietyId; // au,ag
    private String expiredTime;
}
