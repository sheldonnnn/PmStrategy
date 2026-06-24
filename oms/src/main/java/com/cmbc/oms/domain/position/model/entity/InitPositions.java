package com.cmbc.oms.domain.position.model.entity;

import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author chendaqian
 * @date 2026/4/7
 * @time 16:19
 * @description
 */
@Data
public class InitPositions {
    private String InstanceId;//策略实例
    private String traderNo;//交易员号
    private String Symbol;//品种
    private String Side;//买卖方向
    private BigDecimal Qutantity;//累计持仓量
    private BigDecimal Amount;//持仓金额
    private BigDecimal TotalPL;//累计损益
    /**总持仓使用*/
    private BigDecimal OffsetQty;            /*可平量 成交量*/
    private BigDecimal OffsetLastQty;        /*可平昨 昨日持仓量*/
    private BigDecimal OffsetTodayQty;       /*可平今 持仓量*/
    /**合约信息*/
    private ContractInfoBasic contractInfo;
    private String userName;//前端用户
    private String account;//账户（境内：memberId；境外：报价商）
    private String businessType;//业务类型；境内套利 做市 境内外套利

    /**创建建仓初始化参数对象*/
    public InitPositions(String InstanceId, String traderNo, String bsFlag, TotalPosition rspTotalPosition,
                         ContractInfoBasic contract, String account, String userName, String businessType){
        //创建做市品种买入持仓
        /**策略实例*/
        this.InstanceId=InstanceId ;
        /**交易员号*/
        this.traderNo=traderNo;
        /**买卖方向*/
        this.Side=bsFlag;
        /**品种代码*/
        this.contractInfo=contract;

        /**总持仓使用*/
        /**买方*/
        if(BusinessConstant.BUY_SIDE.equals(bsFlag)){
            Positions buyPos = rspTotalPosition.getBuyPos();
            /**持仓金额*/
            this.Amount =buyPos==null ? BigDecimal.ZERO : buyPos.getAmount();
            /**累计损益*/
            this.TotalPL =buyPos==null ? BigDecimal.ZERO : buyPos.getGdAmount().add(buyPos.getFdAmount());
            /**可平量 成交量*/
            this.OffsetQty =buyPos==null ? BigDecimal.ZERO : buyPos.getOffsetQty();
            /**可平昨 昨日持仓量*/
            this.OffsetLastQty =buyPos==null ? BigDecimal.ZERO : buyPos.getOffsetLastQty();
            /**可平今 持仓量*/
            this.OffsetTodayQty =buyPos==null ? BigDecimal.ZERO : buyPos.getOffsetTodayQty();
        }else {
            Positions sellPos = rspTotalPosition.getSellPos();
            /**持仓金额*/
            this.Amount =sellPos==null ? BigDecimal.ZERO : sellPos.getAmount();
            /**累计损益*/
            this.TotalPL =sellPos==null ? BigDecimal.ZERO : sellPos.getGdAmount().add(sellPos.getFdAmount());
            /**可平量 成交量*/
            this.OffsetQty =sellPos==null ? BigDecimal.ZERO : sellPos.getOffsetQty();
            /**可平昨 昨日持仓量*/
            this.OffsetLastQty =sellPos==null ? BigDecimal.ZERO : sellPos.getOffsetLastQty();
            /**可平今 持仓量*/
            this.OffsetTodayQty =sellPos==null ? BigDecimal.ZERO : sellPos.getOffsetTodayQty();
        }
        /**累计持仓量*/
        this.Qutantity =this.OffsetQty;
        this.userName=userName;//前端用户
        this.account=account;//账户（境内：memberId；境外：报价商）
        this.businessType=businessType;//业务类型；境内套利 做市 境内外套利
        this.Symbol = contract.getSymbol();
    }
}
