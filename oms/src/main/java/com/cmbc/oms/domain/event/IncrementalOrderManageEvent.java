package com.cmbc.oms.domain.event;

import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;

import java.util.Map;

/**
 * @author chendaqian
 * @date 2026/3/10
 * @time 19:22
 * @description 增量订单管理明细事件
 */
@EventField(name = "com.finesys.order.statics.IncrementalOrderManage")
public class IncrementalOrderManageEvent {
    // 基础字段
    @EventField(name = "businessType", order = 1)
    private String businessType;        // 业务类型 境内套利 做市 境内外套利
    @EventField(name = "level", order = 2)
    private String level;               // 档位信息：主要针对做市使用，1、2、3档
    @EventField(name = "serviceId", order = 3)
    private String serviceId;           // 服务编号，必须项，区分服务
    @EventField(name = "counterParty", order = 4)
    private String counterParty;        // 交易对手，必须项，外行信息。UBS-Fix、JPMC-Fix等
    @EventField(name = "dataSource", order = 5)
    private String dataSource;          // 数据源，必须项，外资行信息。UBS-Fix、JPMC-Fix等
    @EventField(name = "orderUniqueId", order = 6)
    private String orderUniqueId;       // 委托订单唯一标识
    @EventField(name = "localOrderNo", order = 7)
    private String localOrderNo;        // 本地订单编号
    @EventField(name = "sysOrderNo", order = 8)
    private String sysOrderNo;          // 系统订单编号
    @EventField(name = "matchNo", order = 9)
    private String matchNo;             // 成交号
    @EventField(name = "strategyId", order = 10)
    private String strategyId;          // 策略ID
    @EventField(name = "instanceId", order = 11)
    private String instanceId;          // 策略实例ID
    @EventField(name = "traderNo", order = 12)
    private String traderNo;            // 交易员账户
    @EventField(name = "userName", order = 13)
    private String userName;            // 前台用户名
    @EventField(name = "memberId", order = 14)
    private String memberId;            // 会员号---境内Dimple使用
    @EventField(name = "clientId", order = 15)
    private String clientId;            // 客户号---境内Dimple使用
    @EventField(name = "inventoryType", order = 16)
    private String inventoryType;       // 境内外标识
    @EventField(name = "domesticType", order = 17)
    private String domesticType;        // 境内外标识
    @EventField(name = "symbol", order = 18)
    private String symbol;              // 交易品种
    @EventField(name = "bsFlag", order = 19)
    private String bsFlag;              // 买卖方向
    @EventField(name = "eoFlag", order = 20)
    private String eoFlag;              // 开平方向
    @EventField(name = "shFlag", order = 21)
    private String shFlag;              // 投保标志
    @EventField(name = "orderType", order = 22)
    private String orderType;           // 定单类型
    @EventField(name = "orderAttr", order = 23)
    private String orderAttr;           // 定单属性
    @EventField(name = "tradePurpose", order = 24)
    private String tradePurpose;        // 交易目的
    @EventField(name = "type", order = 25)
    private int type;                   // 订单属性：0正常策略 1平盘策略 2 手工干预策略
    @EventField(name = "exchCode", order = 26)
    private String exchCode;            // 交易所代码
    @EventField(name = "contractInfo", order = 27)
    private String contractInfo;        // 合约信息
    @EventField(name = "qty", order = 28)
    private int qty;                    // 数量
    @EventField(name = "price", order = 29)
    private double price;               // 价格
    @EventField(name = "amount", order = 30)
    private double amount;              // 金额
    @EventField(name = "operateDate", order = 31)
    private String operateDate;         // 操作日期
    @EventField(name = "operateTime", order = 32)
    private String operateTime;         // 操作时间
    @EventField(name = "orderStatus", order = 33)
    private String orderStatus;         // 订单状态
    @EventField(name = "orderStatusCode", order = 34)
    private int orderStatusCode;        
    // 订单状态代码 -1 前置机错误 0委托 1委托确认 2部分成交 3成交 4撤单委托 5撤单成交 6 生成成交回报 7 撤单失败 8 已撤 9 闭市
    @EventField(name = "extraParas", order = 35)
    private Map<String, String> extraParas; // 扩展参数

    public String getBusinessType() { return businessType; }

    public IncrementalOrderManageEvent setBusinessType(String businessType) {
        this.businessType = businessType;
        return this;
    }

    public String getLevel() { return level; }

    public IncrementalOrderManageEvent setLevel(String level) {
        this.level = level;
        return this;
    }

    public String getServiceId() { return serviceId; }

    public IncrementalOrderManageEvent setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public String getCounterParty() { return counterParty; }

    public IncrementalOrderManageEvent setCounterParty(String counterParty) {
        this.counterParty = counterParty;
        return this;
    }

    public String getDataSource() { return dataSource; }

    public IncrementalOrderManageEvent setDataSource(String dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public String getOrderUniqueId() { return orderUniqueId; }

    public IncrementalOrderManageEvent setOrderUniqueId(String orderUniqueId) {
        this.orderUniqueId = orderUniqueId;
        return this;
    }

    public String getLocalOrderNo() { return localOrderNo; }

    public IncrementalOrderManageEvent setLocalOrderNo(String localOrderNo) {
        this.localOrderNo = localOrderNo;
        return this;
    }

    public String getSysOrderNo() { return sysOrderNo; }

    public IncrementalOrderManageEvent setSysOrderNo(String sysOrderNo) {
        this.sysOrderNo = sysOrderNo;
        return this;
    }

    public String getMatchNo() { return matchNo; }

    public IncrementalOrderManageEvent setMatchNo(String matchNo) {
        this.matchNo = matchNo;
        return this;
    }

    public String getStrategyId() { return strategyId; }

    public IncrementalOrderManageEvent setStrategyId(String strategyId) {
        this.strategyId = strategyId;
        return this;
    }

    public String getMemberId() { return memberId; }

    public IncrementalOrderManageEvent setMemberId(String memberId) {
        this.memberId = memberId;
        return this;
    }

    public String getClientId() { return clientId; }

    public IncrementalOrderManageEvent setDomesticType(String domesticType) {
        this.domesticType = domesticType;
        return this;
    }

    public String getSymbol() { return symbol; }

    public IncrementalOrderManageEvent setBsFlag(String bsFlag) {
        this.bsFlag = bsFlag;
        return this;
    }

    public String getShFlag() { return shFlag; }

    public IncrementalOrderManageEvent setShFlag(String shFlag) {
        this.shFlag = shFlag;
        return this;
    }

    public String getOrderAttr() { return orderAttr; }

    public IncrementalOrderManageEvent setOrderAttr(String orderAttr) {
        this.orderAttr = orderAttr;
        return this;
    }

    public String getTradePurpose() { return tradePurpose; }

    public IncrementalOrderManageEvent setTradePurpose(String tradePurpose) {
        this.tradePurpose = tradePurpose;
        return this;
    }

    public int getType() { return type; }

    public IncrementalOrderManageEvent setType(int type) {
        this.type = type;
        return this;
    }

    public String getExchCode() { return exchCode; }

    public IncrementalOrderManageEvent setExchCode(String exchCode) {
        this.exchCode = exchCode;
        return this;
    }

    public String getContractInfo() { return contractInfo; }

    public IncrementalOrderManageEvent setContractInfo(String contractInfo) {
        this.contractInfo = contractInfo;
        return this;
    }

    public int getQty() { return qty; }

    public IncrementalOrderManageEvent setQty(int qty) {
        this.qty = qty;
        return this;
    }

    public double getPrice() { return price; }

    public IncrementalOrderManageEvent setPrice(double price) {
        this.price = price;
        return this;
    }

    public double getAmount() { return amount; }

    public IncrementalOrderManageEvent setAmount(double amount) {
        this.amount = amount;
        return this;
    }

    public String getOperateDate() { return operateDate; }

    public IncrementalOrderManageEvent setOperateDate(String operateDate) {
        this.operateDate = operateDate;
        return this;
    }

    public String getOrderStatus() { return orderStatus; }

    public IncrementalOrderManageEvent setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
        return this;
    }

    public int getOrderStatusCode() { return orderStatusCode; }

    public IncrementalOrderManageEvent setOrderStatusCode(int orderStatusCode) {
        this.orderStatusCode = orderStatusCode;
        return this;
    }

    public Map<String, String> getExtraParas() { return extraParas; }

    public IncrementalOrderManageEvent setExtraParas(Map<String, String> extraParas) {
        this.extraParas = extraParas;
        return this;
    }
}
