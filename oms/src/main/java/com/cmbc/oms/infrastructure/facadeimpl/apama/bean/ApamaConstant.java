package com.cmbc.oms.infrastructure.facadeimpl.apama.bean;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author: Cly
 * @Date: 2026/01/22  17:17
 * @Description:
 */
public class ApamaConstant {
    public static final String BTP_APAMA_CONSTANTS_CHANNEL_CONNECT_UNSUCCESS_STATE = "0";
    public static final String BTP_APAMA_CONSTANTS_CHANNEL_CONNECT_TRYING_STATE = "1";
    public static final String BTP_APAMA_CONSTANTS_CHANNEL_CONNECT_SUCCESS_STATE = "2";
    public static final String WEBSOCKET_TOPIC_CHANNEL_CONNECT = "/topic/channel/connect";
    public static final String MANUAL_DEAL_CHANNEL_NAME = "WEB_CHANNEL";
    public static final String WEB_CHANNEL_OUT_NAME = "WEB_CHANNEL_OUT";

    public ApamaConstant() {
    }

    public static Set<String> getNameSet() {
        Set<String> set = new HashSet<>();
        DataViewEnum[] var1 = DataViewEnum.values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            DataViewEnum adc = var1[var3];
            set.add(adc.getName());
        }

        return set;
    }

    public static Set<DataViewEnum> getDataViewEnumSet() {
        Set<DataViewEnum> set = new HashSet<>();
        DataViewEnum[] var1 = DataViewEnum.values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            DataViewEnum adc = var1[var3];
            set.add(adc);
        }

        return set;
    }

    public static enum DataViewEnum {
        //LC持仓价格数据
        LC_DEPTH_POLY_DATAVIEW("LcDepthPolyDV"),
        //LC订单状态数据
        LC_REST_MANUAL_ORDER_DATAVIEW("LcRestManualOrderDV"),
        /**
         * 规则监控的告警信息
         */
        RISK_RULE_LOG("RiskRuleLogDV"),
        //订单数量验证展示(对外)
        ORDER_QTY_VALID_RSP_DATAVIEW("OrderQtyValidRspDV"),
        //订单数量验证展示(对内)
        MAKER_INNER_ORDER_QTY_VALID_RSP_DATAVIEW("InnerOrderQtyValidRspDV"),
        //报价档位信息展示
        QUOTE_PRICE_BY_QTY_DATAVIEW("QuotePriceByQtyDV"),
        //异常订单dataview
        EXCEPTION_NEW_ORDER_DATAVIEW("ExceptionNewOrderDV"),
        //风控处理响应dataview zm 20200623 add
        WINDOW_SIZE_RSP_DATAVIEW("WindowSizeRspDV"),

        /**
         * zm 2020-06-05 add
         */
        //外汇行情交易头寸响应DATAVIEW
        WAIT_BANK_SQUARING_DATAVIEW("WaitBankSquaringDV"),
        //外汇交易中心成交回报头寸响应DATAVIEW
        WAIT_CFETS_SQUARING_DATAVIEW("WaitCfetsSquaringDV"),
        /**
         * zm 2020-08-09add 交易系统通知信息
         */
        OPERATE_NOTICE_DATAVIEW("OperateNoticeDV"),
        /**
         * 数据源状态变更dataview
         */
        DATA_SOURCE_STATUS_CHANGE_DATAVIEW("BankStateChangeDV"),
        //maker报价前置数据
        QUOTE_PRICE_WEB_DATAVIEW("QuotePriceWebDV", 1),

        /**
         * 行情聚合数据dataview  zm 2019-10-18 增加
         */
        DEPTH_POLY_DATAVIEW("DepthPolyDV", 1),

        /**
         * 外汇行情数据dataview
         */
        EXCHANGE_QUOTATIONS_DATAVIEW("RealtimeMarketDepthDV", 1),

        /**
         * 生成持仓信息，并在前端显示
         */
        POSITION_DATAVIEW("PositionsDV"),

        /**
         * 申报实时推送数据
         */
        FOREIGN_MANUAL_ORDER_REALTIME_DATAVIEW("OrderWebDataDV"),
        /**
         * 订单导源实时推送数据
         */
        SOURCE_ORDER_REALTIME_DATAVIEW("SourceOrderWebDataDV"),

        /**
         * 挂单实时推送数据
         */
        FOREIGN_MANUAL_ORDER_REALTIME_DATAVIEW_REST("RestManualOrderDV"),

        /**
         * 外汇所有订单信息，并在前端显示
         */
        FOREIGN_ALL_ORDER_MANAGER_DATAVIEW("ForeignAllOrderManagerDV"),

        /**
         * LC综合管理订单，并在前端显示
         */
        LC_ALL_ORDER_MANAGER_DATAVIEW("LcOrderManagerDV"),

        /**
         * 授信监控信息(实时)
         * add by HW 2019-10-23
         * com.finesys.dataview.credit.CreditInfoDataView
         */
        CREDIT_INFO_DATAVIEW("CreditInfoDataView"),

        /**
         * 外汇量化交易损益监控
         */
        RISK_PROFIT_LOCKEDDV("RiskProfitLockedDV"),

        /**
         * 平盘订单信息
         */
        AUTO_POSITION_DATAVIEW("AutoPositionSendToWebDV"),

        /**
         * 套利策略行情统计DataView
         */
        ARBITRAGE_STRATEGY_DEPTH_DATAVIEW("DomesticForeignStrategyDepthDV"),

        /**
         * 套利策略数据
         */
        ARBITRAGE_STRATEGY_DATAVIEW("DomesticForeignStrategyDV"),

        /**
         * 策略持仓管理
         */
        ARBITRAGE_POSITION_DATAVIEW("DomesticForeignStrategyPositionDV"),

        /**
         * 套利总持仓管理
         */
        ARBITRAGE_ALL_POSITION_DATAVIEW("ArbitagePositionToWebDV"),

        /**
         * APAMA异常信息
         */
        APAMA_EXCEPTION("ExceptionDV"),

        /**公用返回的信息*/
        COMMON_LOG_RSP_MESSAGE("CommonRspMessageDV"),

        /**老利订单事件返回的信息*/
        ARBITRAGE_ALL_ORDER_MANAGER("ArbitrageAllOrderManagerDV"),

        /**
         * 内部报价行情聚合数据dataview
         */
        INNER_QUOTATION_PRICE_DATAVIEW("InnerQuotePriceDV"),

        /**内部报价监控单笔实时推送数据*/
        INNER_QUOTATION_ORDER_REALTIME_DATAVIEW("InnerOrderWebDataDV"),

        /**内部报价询价行情推送数据*/
        INNER_QUOTATION_RFQ_DEPTH_DATAVIEW("CmbcRfqOfferDepthDV"),

        /**
         * maker对内客侧RFQ询价信息实时推送数据 add hrl 20231025
         */
        MAKER_INNER_RFQ_STATE_DATAVIEW("CmbcRfqStateDV"),

        /**
         * maker对内客侧RFQ询价前置中数量实时推送数据 add hrl 20231101
         */
        MAKER_INNER_RFQ_RUNNING_STATICS_DATAVIEW("CmbcRfqRunningStaticsDV"),

        /**
         * maker对内客侧RFQ询价参考行情实时推送数据 add hrl 20231101
         */
        MAKER_INNER_RFQ_REFER_DEPTH_DATAVIEW("CmbcRfqReferDepthDV"),

        /**
         * maker对内客侧RFQ询价参考行情实时推送数据 add hrl 20231101
         */
        MAKER_INNER_RFQ_OFFER_USER_DATAVIEW("CmbcRfqOfferUserDepthDV"),

        /** maker对外询价行情推送数据
         */
        MAKER_OUTER_RFQ_DEPTH_DATAVIEW("OutRfqOfferDepthDV"),

        /**
         * 外汇CFETS行情聚合数据dataview
         */
        CFETS_PRICE_DATAVIEW("MakerDepthPolyDV"),

        /** 外汇远期ESP行情dataview
         */
        FWD_DEPTH_DATAVIEW("FwdDepthDV"),

        /**
         * 异常日志信息
         */
        EXCEPTION_LOG_INFORMATION("ExceptionLogInformationDV");

        /**
         * dataView名称
         */
        private String name;

        /**
         * 删除标志:在失去apama的连接时是否删除 0:不删除 1:删除
         */
        int delFlag;

        DataViewEnum(String name) {
            this.name = name;
        }

        DataViewEnum(String name, int delFlag) {
            this.name = name;
            this.delFlag = delFlag;
        }

        public String getName() {
            return name;
        }

        public int getDelFlag() {
            return delFlag;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static enum DataViewIgnoreEnum {
        APAMA_DEPTH("com.apama.marketdata.Depth"),
        APAMA_DEPTH_CONTROL_DV("com.apama.marketdata.Depth.Control"),
        APAMA_DEPTH_DATA_DV("com.apama.marketdata.Depth.Data"),
        APAMA_TICK("com.apama.marketdata.Tick"),
        APAMA_TICK_CONTROL_DV("com.apama.marketdata.Tick.Control"),
        APAMA_TICK_DATA_DV("com.apama.marketdata.Tick.Data"),
        APAMA_EXTRA_PARAMS_DV("ExtraParamsDV"),
        APAMA_EXTRA_PARAMS_CONTROL_DV("ExtraParamsDV.Control"),
        APAMA_EXTRA_PARAMS_DATA_DV("ExtraParamsDV.Data"),
        APAMA_ORDER_OPERATIONS_DV("OrderOperationsDV"),
        APAMA_ORDER_OPERATIONS_CONTROL_DV("OrderOperationsDV.Control"),
        APAMA_ORDER_OPERATIONS_DATA_DV("OrderOperationsDV.Data"),
        APAMA_ORDER_PROXY_DV("OrderProxyDV"),
        APAMA_ORDER_PROXY_CONTROL_DV("OrderProxyDV.Control"),
        APAMA_ORDER_PROXY_DATA_DV("OrderProxyDV.Data"),
        APAMA_FIREWALL_RULE_DEFINITION_DV("FirewallRuleDefinitionDataView.Control"),
        APAMA_FIREWALL_RULE_DEFINITION_DATA_DV("FirewallRuleDefinitionDataView.Data"),
        APAMA_MEMST_ORDER_OPERATION_CACHE_DV("MEMST_OrderOperationCache_OrderOperationCache_memory.Control"),
        APAMA_MEMST_ORDER_OPERATION_CACHE_DATA_DV("MEMST_OrderOperationCache_OrderOperationCache_memory.Data"),
        APAMA_RISK_FIREWALL_ALL_RULE_CLASS_INSTANCES_DV("MEMST_RiskFirewall_AllRuleClassInstances_memory.Control"),
        APAMA_RISK_FIREWALL_ALL_RULE_CLASS_INSTANCES_DATA_DV("MEMST_RiskFirewall_AllRuleClassInstances_memory.Data"),
        APAMA_ORDER_SEND_RECEIVE_STATICS_DV("OrderSendReceiveStaticsDV.Control"),
        APAMA_ORDER_SEND_RECEIVE_STATICS_DATA_DV("OrderSendReceiveStaticsDV.Data"),
        APAMA_RISK_FIREWALL_DATAVIEW_DV("RiskFirewallDataView.Control"),
        APAMA_RISK_FIREWALL_DATAVIEW_DATA_DV("RiskFirewallDataView.Data"),
        APAMA_MEMST_ORDER_OPERATION_CACHE_TABLE_DV("MEMST_OrderOperationCache_\\.\\._AP\\_CMF\\_TABLES_memory.Control"),
        APAMA_MEMST_ORDER_OPERATION_CACHE_TABLE_DATA_DV("MEMST_OrderOperationCache_\\.\\._AP\\_CMF\\_TABLES_memory.Data"),
        APAMA_RISK_FIREWALL_ALL_RULE_CLASS_INSTANCES_TABLE_DV("MEMST_RiskFirewall_\\.\\._AP\\_CMF\\_TABLES_memory.Control"),
        APAMA_RISK_FIREWALL_ALL_RULE_CLASS_INSTANCES_DATA_TABLE_DV("MEMST_RiskFirewall_\\.\\._AP\\_CMF\\_TABLES_memory.Data");

        private String name;

        private DataViewIgnoreEnum(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static Set<String> getIgnoreSet() {
            Set<String> set = new HashSet<>();
            DataViewIgnoreEnum[] var1 = values();
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                DataViewIgnoreEnum adc = var1[var3];
                set.add(adc.getName());
            }

            return set;
        }

        public static Set<String> getDvNameSet() {
            Set<String> set = new HashSet<>();
            DataViewIgnoreEnum[] var1 = values();
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                DataViewIgnoreEnum adc = var1[var3];
                set.add("DV_" + adc.getName());
            }

            return set;
        }

        public static List<DataViewIgnoreEnum> getList() {
            List<DataViewIgnoreEnum> list = new ArrayList<>();
            DataViewIgnoreEnum[] items = values();
            if (items != null) {
                list = Arrays.stream(items).collect(Collectors.toList());
            }

            return list;
        }
    }
}
