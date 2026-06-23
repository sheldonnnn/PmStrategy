package com.cmbc.mds.forex.common.constants;

public class BaseConstants {
    // 原有常量
    public static final String LC_SUBSCRIBEID = "subscribeId";
    public static final String FX = "FX";

    public static final String QUOTEID = "quoteId";
    public static final String FXRFQ = "FXRFQ";

    public static final String SERVICE_NAME_KEY1 = "SERVICE_NAME";
    public static final String SERVICE_NAME_KEY2 = "serviceName";
    public static final String SERVICE_NAME_KEY3 = "ServiceName";
    public static final String SERVICE_NAME_DIMPLE = "DIMPLE";
    public static final String SERVICE_NAME_CMDS = "CMDS";
    public static final String SERVICE_NAME_CMBC = "CMBC";

    public static final String DEPTH_EXTRAKEY_ERROR = "_ERROR";

    public static final String TRADE_MODE_ODM = "1";
    public static final String TRADE_MODE_QDM = "0";

    public static final String INTERNAL_QUOTATION = "InternalQuotation"; // 对内报价
    public static final String EXTERNAL_QUOTATION = "ExternalQuotation"; // 对外报价

    public static final String QUOTE_ENYRY_ID = "117";

    // ================== 新增常量 ==================

    // 行情源
    public static final String PROVIDER_FXALL = "FXALL";

    // 状态相关
    public static final String STATUS_CONNECTED = "1"; // 连接状态：已连接
    public static final String STATUS_DISCONNECTED = "0"; // 连接状态：连接已断开

    public static final String IS_ONE_MAKER_NO = "0"; // 多数据源
    public static final String IS_ONE_MAKER_YES = "1"; // 单数据源

    // 扩展字段 Key
    public static final String KEY_EXNM = "exnm";
    public static final String KEY_NAMEID = "nameid";
    public static final String KEY_TPFG = "tpfg";
    public static final String KEY_TERM = "term";
    public static final String KEY_CXFG = "cxfg";
    public static final String KEY_STFG = "stfg";
    public static final String KEY_TRFG = "trfg";
    public static final String KEY_STRIKE = "strike";
    public static final String KEY_VALUEDAY = "valueDay";
    public static final String KEY_INTIME = "intime";
    public static final String KEY_OUTTIME = "outtime";

    // 价格相关 Key 后缀/前缀
    public static final String KEY_CURRENCY_SUFFIX = "_Currency";
    public static final String KEY_EXPIRE_TIME_SUFFIX = "_ExpireTime";
    public static final String KEY_BID_PREFIX = "BID";
    public static final String KEY_ASK_PREFIX = "ASK";

    // 特定业务字段 Key
    public static final String KEY_1301 = "1301"; // LC 特殊字段
    public static final String KEY_1300 = "1300"; // 聚合属性特殊字段
    public static final String KEY_ASK_ORIGINATOR = "askOriginator";
    public static final String KEY_BID_ORIGINATOR = "bidOriginator";
    public static final String KEY_ASK2_SEQ = "ask2Seq";
    public static final String KEY_BID2_SEQ = "bid2Seq";

    // 特殊服务名与值
    public static final String SERVICE_LC_FIX = "LC-FIX";
    public static final String SERVICE_LCQDM_FIX = "LCQDM-FIX";
    public static final String SERVICE_LCODM_FIX = "LCODM-FIX";
    public static final String VAL_MARKET_ID_FXQDM = "FXQDM";

    // 【修改】原 Handler 前缀改为 Adapter 前缀
     public static final String HANDLER_BEAN_PREFIX = "Handler_"; // 旧值
    public static final String ADAPTER_BEAN_PREFIX = "Adapter_"; // 新值

    // 聚合常量
    public static final String CONST_ASK_SPOT_RATE = "LC_ASKSPOTRATE";
    public static final String CONST_BID_SPOT_RATE = "LC_BIDSPOTRATE";
    public static final String CONST_ASK_FWD_POINTS = "LC_ASKFORWARDPOINTS";
    public static final String CONST_BID_FWD_POINTS = "LC_BIDFORWARDPOINTS";
    public static final String CONST_ASK_SWAP_POINTS = "LC_ASKSWAPPOINTS";
    public static final String CONST_BID_SWAP_POINTS = "LC_BIDSWAPPOINTS";

    // 聚合行情KEY前缀
    public static final String MARKET_DATA_CLEAN_KEY_PREFIX = "MD:CLEAN:";
    public static final String MARKET_DATA_MERGE_KEY_PREFIX = "MD:MERGE:";
    public static final String MARKET_DATA_DIST_KEY_PREFIX = "DIST:";

    // 境内外标识
    public static final String DOMESTIC_TYPE_INNER = "0";
    public static final String DOMESTIC_TYPE_OUTER = "1";

    // mq报文类型
    public static final String MESSAGE_TYPE_HEARTBEATRESPONSE = "HeartbeatResponse";
    public static final String MESSAGE_TYPE_SUBSCRIBEREJECTDEPTH = "SubscribeRejectDepth";
    public static final String MESSAGE_TYPE_MQTRANSERBEAN = "MQTranserBean";

}
