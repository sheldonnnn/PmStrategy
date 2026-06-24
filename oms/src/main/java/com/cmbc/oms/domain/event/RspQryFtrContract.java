package com.cmbc.oms.domain.event;

import java.util.HashMap;

/**
 * @author chendaqian
 * @date 2026/4/8
 * @time 15:11
 * @description 期货合约响应
 */
public class RspQryFtrContract {
    private String uniqueID;            //唯一ID
    private String ExchCode;            /*交易所代码*/
    private String ContractID;          /*合约编码*/
    private String Currency;            /*币种*/
    private String ContractName;        /*合约名称*/
    private String VarietyId;           /*品种代码*/
    private Integer Unit;               /*每手乘数*/
    private String MeasureUnit;         /*计价单位*/
    float Tick;                         /*最小价位*/
    private Integer MaxHand;            /*限价单最大可下单手数*/
    private Integer MinHand;            /*限价单最小可下单手数*/
    private Integer MaxMarketOrderVolume;/*市价单最大下单量*/
    private Integer MinMarketOrderVolume;/*市价单最小下单量*/
    private float RefPrice;             /*挂牌基准价*/
    private String Status;              /*合约状态*/
    private String EndDeliveryDate;     /*终止交割日期*/
    private float RiseLimit;            /*涨停板*/
    private float FallLimit;            /*跌停板*/

    private boolean isSuccess;
    private boolean bIsLast;
    HashMap<String, String> extraParas;
}
