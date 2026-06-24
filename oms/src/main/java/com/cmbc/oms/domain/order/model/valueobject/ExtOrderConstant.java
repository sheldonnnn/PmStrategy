package com.cmbc.oms.domain.order.model.valueobject;

/**
 * @author chendaqian
 * @date 2026/2/11
 * @time 14:56
 * @description
 */
public class ExtOrderConstant {
    /**业务类型*/
    private String BUSINESS_TYPE_NAME = "BusinessType";
    /**serviceID标识*/
    private String SERVICE_ID_NAME = "ServiceID";
    /**订单发送serviceID标识*/
    private String OMS_SEND_SERVICE_ID_NAME = "omsSendServiceID";
    /**会员号标识*/
    private String MEMBER_ID_NAME = "MemberId";
    /**客户号标识*/
    private String CLIENT_ID_NAME = "ClientId";
    /**交易员信息*/
    private String TRADER_NO_NAME = "TraderNo";
    /**本地订单号*/
    private String LOCAL_ORDER_NO_NAME = "LocalOrderNo";
    /**成交单号*/
    private String MATCH_NO_NAME = "MatchNo";
    /**投保标志*/
    private String SH_FLAG_NAME = "ShFlag";
    /**开平标志*/
    private String EO_FLAG_NAME = "EoFlag";
    /**档位*/
    private String LEVEL_NAME = "Level";
    /**交易属性 区分市价单和限价单，限价单：0，市价单：1*/
    private String ORDER_TYPE_NAME = "OrderType";
    /**交易属性 0无 1全部成交 2立即成交*/
    private String ORDER_ATTR_NAME = "OrderAttr";
    /**交易所代码*/
    private String EXCH_CODE_NAME = "ExchCode";
    /**品种代码*/
    private String VARIETY_ID_NAME = "VarietyId";
    /**最小价位*/
    private String TICK_NAME = "Tick";
    /**每手乘数*/
    private String UNIT_NAME = "Unit";
    /**计价单位*/
    private String MEASURE_UNIT_NAME = "MeasureUnit";
    /**合约类型 0现货 1期货 递延2*/
    private String INVENTORY_TYPE = "InventoryType";
    /**境内外标识 0 境内 1 境外*/
    private String DOMESTIC_TYPE_NAME = "DomesticType";
    /**终止交割日期*/
    private String END_DELIVERY_DATE_NAME = "EndDeliveryDate";
    /**是否历史合约*/
    private String IS_HISTORY_CONTRACT_NAME = "IsHistoryContract";
    /**@date 20221125 兼容贵金属外汇基础合约信息字段*/
    /**货币对*/
    private String SYMBOL_NAME = "Symbol";
    /**精度*/
    private String ACCURACY_NAME = "accuracy";
    private String SETTLE_TIME_NAME = "64";
    /**结算字段名称*/
    private String SETTLE_TIME_KEY = "settleTime";
    /**交易目的 TL套利*/
    private String TRADE_PURPOSE_NAME = "TradePurpose";
    /**订单类型*/
    private String TYPE_NAME = "Type";
    /**订单开平类型*/
    private String OPENING_CLOSING_TYPE = "OpeningClosingType";
    /**订单时间*/
    private String ORDER_TIME_STAMP_NAME = "OrderTimeStamp";
    /**成交时间戳*/
    private String MATCH_TIME_STAMP_NAME = "MatchTimeStamp";
    /**套利合约号*/
    private String ACT_ARBI_CONTRACT_ID_NAME = "ActArbiContractId";
    /**多合约套利key*/
    private String MULTI_KEY = "MultiKey";
    /**成交量*/
    private String LAST_QTY_NAME = "LastQty";
    /**成交金额*/
    private String CALCULATED_CCY_LAST_QTY_NAME = "CalculatedCcyLastQty";
    /**通道名称*/
    private String CHENAL_NAME = "ChanelName";
    /**流动性不足Liquidity not available at requested price*/
    /**价格不足 Market movement invalidated quote*/
    private String RENEW_ORDER = "yes";
    /**订单状态码*/
    private String ORDER_STATUS_NAME = "orderStatus";
    /**委托订单*/
    private String NEW_ORDER = "NewOrder";
    /**成交订单*/
    private String DEAL_ORDER = "DealOrder";
    /**撤单订单*/
    private String CANCEL_ORDER = "CancelOrder";
    /**市场编号*/
    private String MARKET_ORDER_ID = "MarketOrderId";
}
