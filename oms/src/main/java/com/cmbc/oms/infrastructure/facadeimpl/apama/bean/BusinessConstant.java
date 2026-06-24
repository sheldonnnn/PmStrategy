package com.cmbc.oms.infrastructure.facadeimpl.apama.bean;

import java.math.BigDecimal;

/**
 * @Author: Cly
 * @Date: 2026/01/23  12:17
 * @Description:
 */
public class BusinessConstant {

    /**
     * 拟处理长度常量
     */
    public static int BATCH_LENGTH = 100;

    /*************************************************************************
     * 业务存量常量
     ************************************************************************/
    //Apama-ScenarioDefinitionListener 数据缓存标记
    public static final String SCENARIO_DEFINITION_DATA = "business:apama:dataView:";

    /**
     * DATAVIEW条目变化事件类型
     */
    public enum DataViewEventType {
        ADD,
        UPDATE,
        ADD_OR_UPDATE,
        DELETE
    }

    /**
     * 向前台推送的消息等级
     */
    public enum MessageGrade {
        TRACE(0, "消息"),
        INFO(1, "提示"),
        WARN(2, "警告"),
        ERROR(3, "错误");

        MessageGrade(int grade, String gradeName) {
            this.grade = grade;
            this.gradeName = gradeName;
        }

        public int grade;
        public String gradeName;
    }

    /**
     * DIMPLE系统的连接状态
     */
    public enum DimpleStatus {
        CLOSED("closed"),
        OPENED("opened");

        public String name;

        DimpleStatus(String name) { this.name = name; }
    }

    /**
     * Apama异常信息结算时间-时
     */
    public static int APAMA_EXCEPTION_CLEAR_HOUR = 19;
    /**
     * Apama异常信息结算时间-分
     */
    public static int APAMA_EXCEPTION_CLEAR_MINUTE = 0;
    /**
     * Apama异常信息结算时间-秒
     */
    public static int APAMA_EXCEPTION_CLEAR_SECOND = 0;

    /**市场字典Type类别*/
    public static String DICT_MAKERINDICATOR_TYPE = "makerIndicator_type";
    /**交易模型字典Type类别*/
    public static String DICT_TRADEINSTRUMENT_TYPE = "tradeInstrument_type";
    /**交易方式字典Type类别*/
    public static String DICT_TRADEMETHOD_TYPE = "tradeMethod_type";
    /**交易模式字典Type类别*/
    public static String DICT_TRADINGMODE_TYPE = "tradingMode_type";
    /**数据来源字典Type类别*/
    public static String DICT_DATASOURCE_TYPE = "dataSource_type";
    /**交易方向Type类别*/
    public static String DICT_SIDE_TYPE = "side_type";
    /**订单状态Type类别*/
    public static String DICT_ORDER_STATUS_TYPE = "orderStatus_type";
    /**
     * 增加系统参数类型的常量 zm 1120
     */

    /**自动平盘类型*/
    public static String POSITION_SQUARING_SYS_PARAMAS = "PositionSquaringSysParams";
    /**异常订单超时时间*/
    public static String ORDER_TIME_VALID_PARAMAS = "OrderTimeValidParams";
    /**下单数量计算单位类型*/
    public static String ORDER_QUANTITY_UNIT_TYPE = "order_quantity_unit";

    /**
     * 是/否
     */
    public static final String YES = "1";
    public static final String NO = "0";

    /**
     * 订单买卖标记
     */
    public static final String ORDER_BUY = "1";
    public static final String ORDER_SELL = "3";

    /**
     * 策略暂停和启动标识
     */
    public static final String STRATEGY_INSTANCE_STATUS_PAUSE = "0";
    public static final String STRATEGY_INSTANCE_STATUS_CONTINUE = "1";

    /**套利服务名*/
    public static String DomesticForeignArbitrageServiceName="DemosticForeignArbitragey";

    /**套利合约品种*/
    public static String[] ArbitrageSymbols={"USD/CNY","USD/CNH"};

    /**业务类型:外汇境内外创金业务*/
    public static String BUSINESS_TYPE_FOREIGN_ABROAD="ForeignAbroad";
    /**业务类型:外汇对内报价业务*/
    public static String BUSINESS_TYPE_FOREIGN_INNER="ForeignInner";
    /**业务类型:外汇手工下单业务*/
    public static String BUSINESS_TYPE_FOREIGN_MANAUL="ForeignManual";
    /**业务类型:外汇做市业务*/
    public static String BUSINESS_TYPE_FOREIGN_MAKER="ForeignMaker";


    /**挂单类型:cmbc 对外*/
    public static String ORDER_TYPE_FOREIGN="0";
    /**业务类型:外汇做市业务*/
    public static String ORDER_TYPE_CMBC="1";
    /**
     * 交易系统参数 是否允许自成交
     */
    public static final String SYSTEM_TRADE_ORDER_SELF_PARAM_LABEL = "selfOrderParam";

    // DomesticType 境内0，境外1
    public static final String DOMESTIC_TYPE_INNER = "0";
    public static final String DOMESTIC_TYPE_OUTER = "1";

    // InventoryType 投资类型
    /**现货*/
    public static final String SPOT = "0";
    /**合约*/
    public static final String CONTRACT = "1";
    /**递延*/
    public static final String DEFERRED = "2";

    // side 买卖方向
    public static final String BUY_SIDE = "BUY";
    public static final String SELL_SIDE = "SELL";

    // dimple 买卖标志
    public static final String FLAT_TODAY_POSITION="5";

    /**投资标志更改 3 保值*/
    public static final String SH_FLAG="3";

    /**上海期交所 01*/
    public static final String SH_FUTURES_EXCHANGE="01";
    /**上海黄金交易所 02*/
    public static final String SH_GOLD_EXCHANGE="02";

    /**下单量控制-订单处理结果 true:下单，false:拒单*/
    public static final String ORDER_QTY_VALID_RESULT = "orderQtyValidResult";
    /**下单量控制-订单处理完成标识*/
    public static final String ORDER_QTY_VALID_COMPLETE = "orderQtyValidComplete";
    /**外汇行情单成交回报返回扩展字段中交易币种key*/
    public static final String ORD_CURRENCY_KEY = "Currency";

    /**是否异常订单*/
    public static final String IS_EXCEPTION_ORDER_NAME = "isExceptionOrder";
    /**是否异常订单*/
    public static final String IS_EXCEPTION_ORDER = "1";
    /**错误ID*/
    public static final String ERROR_ID_NAME = "ErrorID";
    /**错误信息*/
    public static final String ERROR_MSG_NAME = "ErrorMsg";
    /**订单未委托确认就订单已完成，无法进行撤单！*/
    public static final String ENTRUST_40001="40001";
    /**已撤*/
    public static final String ENTRUST_40039="40039";
    /**无法撤单，订单已完全成交*/
    public static final String ENTRUST_40040="40040";
    /**响应超时*/
    public static final String ENTRUST_EXPIRE_TIME="11";

    // 管理台合约操作type
    public static final String delete="0"; //删除
    public static final String insert="1"; //增加
    public static final String update="2"; //修改

    // 持仓相关
    /**盎司*/
    public static final String OUNCE ="ounce";
    /**盎司与克换算比例*/
    public static final BigDecimal OUNCE_GRAM=new BigDecimal("31.1035");
    /**手*/
    public static final String HAND ="hand";
    /**1000*/
    public static final Integer THOUSAND =1000;
    /**千克*/
    public static final String KILO_GRAM ="kg";
    /**克*/
    public static final String GRAM ="g";
    /**统计单位kg*/
    public static final BigDecimal STATIC_UNIT = new BigDecimal("1000.0");
    /**雷亚*/

    /**白银*/
    public static final String AG ="ag";
    /**白银*/
    public static final String WH ="wh";

    /**计算价格类型 avePrice:计算平均价 */
    public static final String CAL_TYPE_AVE ="avePrice";
    /**计算价格类型 totalPrice:计算总金额*/
    public static final String CAL_TYPE_TOTAL ="totalPrice";
    //境外交易场所标识
    public static final String GS_SERVICE_NAME = "GS";
    //订单类型
    public static final String QUOTED_ORDER ="PREVIOUSLY QUOTED";
    public static final String LIMIT_ORDER ="LIMIT";
    public static final String MARKET_ORDER ="MARKET";

    // 订单标签 tag
    public static final String ORDER_TAG_TYPE_MGAPHEDGE = "GOLD_HEDGE";
    public static final String BUSINESS_TYPE_MANUAL = "Manual";

}
