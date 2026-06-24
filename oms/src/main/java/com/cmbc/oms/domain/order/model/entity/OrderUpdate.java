package com.cmbc.oms.domain.order.model.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * @author chendaqian
 * @date 2026/2/26
 * @time 14:25
 * @description 订单更新报文实体
 */
@Data
public class OrderUpdate {
    private String orderId;
    private String symbol;
    private BigDecimal price;
    private String side;
    private String orderType;        // limit等
    private BigDecimal quantity;
    private boolean sendConfirm;     // 是否已委托确认
    private boolean send;            // 是否已委托
    private boolean inMarket;        // 是否在市场中
    private boolean rejected;        // 是否拒绝
    private boolean timeout;         // 是否超时
    private boolean error;           // 是否错误
    private boolean cancelState;     // 是否为已撤单状态
    private String sysMatchNo;       // 系统成交编号
    private BigDecimal dealQty;      // 成交数量
    private BigDecimal matchAmount;  // 成交金额
    private String status;           // 成交状态
    private BigDecimal lastPrice;    // 成交价格
    private BigDecimal avgPrice;     // 成交平均价格
    private String matchDescription;
    private String counterParty;
    private BigDecimal leaveQty;

    // 扩展信息
    /**
     * 订单数量
     */
    private BigDecimal orderQty;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 授信验证完成标志
     */
    private String creditValidateComplete;

    /**
     * 客户号
     */
    private String clientId;

    /**
     * 货币
     */
    private String endDeliveryDate;

    /**
     * 开平标志
     */
    private String eoFlag;

    /**
     * 合约类型
     */
    private String inventoryType;

    /**
     * 是否历史合约
     */
    private String isHistoryContract;

    /**
     * 档位
     */
    private String level;

    /**
     * 本地订单号
     */
    private String localOrderNo;

    /**
     * 市场
     */
    private String market;

    /**
     * 计价单位
     */
    private BigDecimal measureUnit;

    /**
     * 品种代码
     */
    private String varietyId;

    /**
     * 数据源
     */
    private String dataSource;

    /**
     * 过期时间
     */
    private String expiredTime;

    /**
     * 订单时间有效时间
     */
    private String orderTimeValidTime;

    /**
     * 步长
     */
    private String stepPosition;

    // 特有字段
    /**
     * 错误信息
     * 委托订单响应报文："ErrorMsg":"委托订单"
     * 委托确认响应报文："ErrorMsg":"委托确认"
     * 全部成交响应报文："ErrorMsg":"全部成交"
     */
    private String errorMsg;

    /**
     * 错误ID
     * 委托订单响应报文："ErrorId":"0"
     * 委托确认响应报文："ErrorId":"1"
     * 全部成交响应报文："ErrorId":"-3"
     */
    private String errorId;

    /**
     * 系统订单编号
     * 全部成交响应报文特有的字段
     */
    private String sysOrderNo;

    /**
     * 本地成交时间戳
     * 全部成交响应报文特有的字段
     */
    private String localMatchTimeStamp;

    /**
     * 成交日期
     * 全部成交响应报文特有的字段
     */
    private String matchDate;

    /**
     * 成交编号
     * 全部成交响应报文特有的字段
     */
    private String matchNo;

    /**
     * 成交时间
     * 全部成交响应报文特有的字段
     */
    private String matchTime;

    /**
     * 成交时间戳
     * 全部成交响应报文特有的字段
     */
    private String matchTimeStamp;

    // (根据图2补充被折叠的字段)
    private String creditResult;
    private String exchCode;
    private String exchange;
    private String forkPlate;
    private String memberId;
    private String omsIssueTime;
    private String orderAttr;
    private String orderDate;
    private String orderTimeStamp;
    private String positionTagName;
    private String serviceName;
    private String shFlag;
    private String straightDiscForkType;
    private String strategyId;
    private String strategyInstanceId;
    private String systemId;
    private BigDecimal tick;
    private String tradePurpose;
    private String traderNo;
    private String type;
    private BigDecimal unit;
    private String userName;
    private BigDecimal accuracy;

    public String getOrderId() { return orderId; }

    public OrderUpdate setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getSymbol() { return symbol; }

    public OrderUpdate setSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public BigDecimal getPrice() { return price; }

    public OrderUpdate setPrice(BigDecimal price) {
        this.price = price;
        return this;
    }

    public OrderUpdate setSide(String side) {
        this.side = side;
        return this;
    }

    public OrderUpdate setSend(boolean send) {
        this.send = send;
        return this;
    }

    public boolean isInMarket() { return inMarket; }

    public OrderUpdate setInMarket(boolean inMarket) {
        this.inMarket = inMarket;
        return this;
    }

    public boolean isRejected() { return rejected; }

    public OrderUpdate setRejected(boolean rejected) {
        this.rejected = rejected;
        return this;
    }

    public boolean isTimeout() { return timeout; }

    public OrderUpdate setTimeout(boolean timeout) {
        this.timeout = timeout;
        return this;
    }

    public boolean isError() { return error; }

    public OrderUpdate setError(boolean error) {
        this.error = error;
        return this;
    }

    public boolean isCancelState() { return cancelState; }

    public OrderUpdate setCancelState(boolean cancelState) {
        this.cancelState = cancelState;
        return this;
    }

    public String getSysMatchNo() { return sysMatchNo; }

    public OrderUpdate setSysMatchNo(String sysMatchNo) {
        this.sysMatchNo = sysMatchNo;
        return this;
    }

    public BigDecimal getDealQty() { return dealQty; }

    public OrderUpdate setDealQty(BigDecimal dealQty) {
        this.dealQty = dealQty;
        return this;
    }

    public BigDecimal getMatchAmount() { return matchAmount; }

    public OrderUpdate setMatchAmount(BigDecimal matchAmount) {
        this.matchAmount = matchAmount;
        return this;
    }

    public String getStatus() { return status; }

    public OrderUpdate setStatus(String status) {
        this.status = status;
        return this;
    }

    public BigDecimal getLastPrice() { return lastPrice; }

    public OrderUpdate setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
        return this;
    }

    public BigDecimal getAvgPrice() { return avgPrice; }

    public OrderUpdate setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
        return this;
    }

    public String getMatchDescription() { return matchDescription; }

    public OrderUpdate setMatchDescription(String matchDescription) {
        this.matchDescription = matchDescription;
        return this;
    }

    public String getCounterParty() { return counterParty; }

    public OrderUpdate setCounterParty(String counterParty) {
        this.counterParty = counterParty;
        return this;
    }

    public BigDecimal getLeaveQty() { return leaveQty; }

    public OrderUpdate setLeaveQty(BigDecimal leaveQty) {
        this.leaveQty = leaveQty;
        return this;
    }

    public BigDecimal getOrderQty() { return orderQty; }

    public OrderUpdate setOrderQty(BigDecimal orderQty) {
        this.orderQty = orderQty;
        return this;
    }

    public String getCreditValidateComplete() { return creditValidateComplete; }

    public OrderUpdate setCreditValidateComplete(String creditValidateComplete) {
        this.creditValidateComplete = creditValidateComplete;
        return this;
    }

    public String getClientId() { return clientId; }

    public OrderUpdate setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getCreditResult() { return creditResult; }

    public OrderUpdate setCreditResult(String creditResult) {
        this.creditResult = creditResult;
        return this;
    }

    public String getEndDeliveryDate() { return endDeliveryDate; }

    public OrderUpdate setEndDeliveryDate(String endDeliveryDate) {
        this.endDeliveryDate = endDeliveryDate;
        return this;
    }

    public String getEoFlag() { return eoFlag; }

    public OrderUpdate setEoFlag(String eoFlag) {
        this.eoFlag = eoFlag;
        return this;
    }

    public String getExchCode() { return exchCode; }

    public OrderUpdate setExchCode(String exchCode) {
        this.exchCode = exchCode;
        return this;
    }

    public String getExchange() { return exchange; }

    public OrderUpdate setExchange(String exchange) {
        this.exchange = exchange;
        return this;
    }

    public String getForkPlate() { return forkPlate; }

    public OrderUpdate setForkPlate(String forkPlate) {
        this.forkPlate = forkPlate;
        return this;
    }

    public String getInventoryType() { return inventoryType; }

    public OrderUpdate setInventoryType(String inventoryType) {
        this.inventoryType = inventoryType;
        return this;
    }

    public String getIsHistoryContract() { return isHistoryContract; }

    public OrderUpdate setIsHistoryContract(String isHistoryContract) {
        this.isHistoryContract = isHistoryContract;
        return this;
    }

    public String getLevel() { return level; }

    public OrderUpdate setLevel(String level) {
        this.level = level;
        return this;
    }

    public String getLocalOrderNo() { return localOrderNo; }

    public OrderUpdate setLocalOrderNo(String localOrderNo) {
        this.localOrderNo = localOrderNo;
        return this;
    }

    public String getMarket() { return market; }

    public OrderUpdate setMarket(String market) {
        this.market = market;
        return this;
    }

    public BigDecimal getMeasureUnit() { return measureUnit; }

    public OrderUpdate setMeasureUnit(BigDecimal measureUnit) {
        this.measureUnit = measureUnit;
        return this;
    }

    public String getMemberId() { return memberId; }

    public OrderUpdate setMemberId(String memberId) {
        this.memberId = memberId;
        return this;
    }

    public String getOmsIssueTime() { return omsIssueTime; }

    public OrderUpdate setOmsIssueTime(String omsIssueTime) {
        this.omsIssueTime = omsIssueTime;
        return this;
    }

    public String getOrderAttr() { return orderAttr; }

    public OrderUpdate setOrderAttr(String orderAttr) {
        this.orderAttr = orderAttr;
        return this;
    }

    public String getOrderDate() { return orderDate; }

    public OrderUpdate setOrderDate(String orderDate) {
        this.orderDate = orderDate;
        return this;
    }

    public String getOrderTimeStamp() { return orderTimeStamp; }

    public OrderUpdate setOrderTimeStamp(String orderTimeStamp) {
        this.orderTimeStamp = orderTimeStamp;
        return this;
    }

    public String getPositionTagName() { return positionTagName; }

    public OrderUpdate setPositionTagName(String positionTagName) {
        this.positionTagName = positionTagName;
        return this;
    }

    public String getServiceName() { return serviceName; }

    public OrderUpdate setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getShFlag() { return shFlag; }

    public OrderUpdate setShFlag(String shFlag) {
        this.shFlag = shFlag;
        return this;
    }

    public String getStraightDiscForkType() { return straightDiscForkType; }

    public OrderUpdate setStraightDiscForkType(String straightDiscForkType) {
        this.straightDiscForkType = straightDiscForkType;
        return this;
    }

    public String getStrategyId() { return strategyId; }

    public OrderUpdate setStrategyId(String strategyId) {
        this.strategyId = strategyId;
        return this;
    }

    public String getStrategyInstanceId() { return strategyInstanceId; }

    public OrderUpdate setStrategyInstanceId(String strategyInstanceId) {
        this.strategyInstanceId = strategyInstanceId;
        return this;
    }

    public String getSystemId() { return systemId; }

    public OrderUpdate setSystemId(String systemId) {
        this.systemId = systemId;
        return this;
    }

    public BigDecimal getTick() { return tick; }

    public OrderUpdate setTick(BigDecimal tick) {
        this.tick = tick;
        return this;
    }

    public String getTradePurpose() { return tradePurpose; }

    public OrderUpdate setTradePurpose(String tradePurpose) {
        this.tradePurpose = tradePurpose;
        return this;
    }

    public String getTraderNo() { return traderNo; }

    public OrderUpdate setTraderNo(String traderNo) {
        this.traderNo = traderNo;
        return this;
    }

    public String getType() { return type; }

    public OrderUpdate setType(String type) {
        this.type = type;
        return this;
    }

    public BigDecimal getUnit() { return unit; }

    public OrderUpdate setUnit(BigDecimal unit) {
        this.unit = unit;
        return this;
    }

    public String getUserName() { return userName; }

    public OrderUpdate setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getVarietyId() { return varietyId; }

    public OrderUpdate setVarietyId(String varietyId) {
        this.varietyId = varietyId;
        return this;
    }

    public BigDecimal getAccuracy() { return accuracy; }

    public OrderUpdate setAccuracy(BigDecimal accuracy) {
        this.accuracy = accuracy;
        return this;
    }

    public String getDataSource() { return dataSource; }

    public OrderUpdate setDataSource(String dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public String getExpiredTime() { return expiredTime; }

    public OrderUpdate setExpiredTime(String expiredTime) {
        this.expiredTime = expiredTime;
        return this;
    }

    public String getOrderTimeValidTime() { return orderTimeValidTime; }

    public OrderUpdate setOrderTimeValidTime(String orderTimeValidTime) {
        this.orderTimeValidTime = orderTimeValidTime;
        return this;
    }

    public String getStepPosition() { return stepPosition; }

    public OrderUpdate setStepPosition(String stepPosition) {
        this.stepPosition = stepPosition;
        return this;
    }

    public String getErrorMsg() { return errorMsg; }

    public OrderUpdate setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
        return this;
    }

    public String getErrorId() { return errorId; }

    public OrderUpdate setErrorId(String errorId) {
        this.errorId = errorId;
        return this;
    }

    public String getSysOrderNo() { return sysOrderNo; }

    public OrderUpdate setSysOrderNo(String sysOrderNo) {
        this.sysOrderNo = sysOrderNo;
        return this;
    }

    public String getLocalMatchTimeStamp() { return localMatchTimeStamp; }

    public OrderUpdate setLocalMatchTimeStamp(String localMatchTimeStamp) {
        this.localMatchTimeStamp = localMatchTimeStamp;
        return this;
    }

    public String getMatchDate() { return matchDate; }

    public OrderUpdate setMatchDate(String matchDate) {
        this.matchDate = matchDate;
        return this;
    }

    public String getMatchNo() { return matchNo; }

    public OrderUpdate setMatchNo(String matchNo) {
        this.matchNo = matchNo;
        return this;
    }

    public String getMatchTime() { return matchTime; }

    public OrderUpdate setMatchTime(String matchTime) {
        this.matchTime = matchTime;
        return this;
    }

    public String getMatchTimeStamp() { return matchTimeStamp; }

    public OrderUpdate setMatchTimeStamp(String matchTimeStamp) {
        this.matchTimeStamp = matchTimeStamp;
        return this;
    }
}
