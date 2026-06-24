package com.cmbc.oms.domain.position.model.entity;

import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author chendaqian
 * @date 2026/2/28
 * @time 15:20
 * @description 总持仓
 */
@Data
public class TotalPosition {

    private String traderNo;//交易员号
    private String bsFlag;//买卖方标志
    private String account;//账户
    private String symbol;//交易品种
    private BigDecimal totalQty;//累计持仓量
    private ContractInfoBasic contractInfo;//合约信息
    private Positions buyPos;//买方持仓
    private Positions sellPos;//卖方持仓
    /**总持仓使用*/
    private BigDecimal OffsetQty;            /*可平量 成交量*/
    private BigDecimal OffsetLastQty;        /*可平昨 昨日持仓量*/
    private BigDecimal OffsetTodayQty;       /*可平今 持仓量*/

    /**
     *
     * @param traderNo 交易员号 必填
     * @param account 会员编号 必填
     * @param symbol 交易品种 必填
     * @param bsFlag 买卖方标志 必填
     * @param qty 可用持仓量
     * @param LastQty 上日持仓量
     * @param todayQty 今日持仓量
     * @param offsetQty 可平量 成交量
     * @param offsetLastQty 可平昨 未成交数量
     * @param offsetTodayQty 可平今 撤单数量
     * @param frozenQty 冻结持仓量
     * @param totalQty 总持仓量
     * @param amt 持仓额
     * @param margin 保证金
     * @param avePrice 持仓均价
     * @param lastPrice 最新价
     * @param profitFund 平仓盈亏
     * @param floatProfitLoss 持仓盈亏
     * @param tradePurpose 交易目的
     * @return
     */
    public TotalPosition createTotalPosition( String traderNo, String account, 
                                              String symbol, String bsFlag, BigDecimal qty, BigDecimal LastQty, BigDecimal todayQty,
                                              BigDecimal offsetQty, BigDecimal offsetLastQty, BigDecimal offsetTodayQty,
                                              BigDecimal frozenQty, BigDecimal totalQty, BigDecimal amt, BigDecimal margin,
                                              BigDecimal avePrice, BigDecimal lastPrice, BigDecimal profitFund,
                                              BigDecimal floatProfitLoss,
                                              String tradePurpose,ContractInfoBasic contract) {
        TotalPosition rspTotalPosition = new TotalPosition();
        rspTotalPosition.setBsFlag( bsFlag);
        rspTotalPosition.setTraderNo(traderNo);
        rspTotalPosition.setAccount(account);
        rspTotalPosition.setSymbol( symbol);
        rspTotalPosition.setContractInfo(contract);
        /**买方持仓*/
        if(BusinessConstant.BUY_SIDE.equals( bsFlag)){
            Positions buyPosition = new Positions();
            buyPosition.setQutantity(qty); //可用持仓量
            buyPosition.setOffsetQty(offsetQty);//可平量 成交量
            buyPosition.setOffsetLastQty(offsetLastQty);/*可平昨 未成交数量*/
            buyPosition.setOffsetTodayQty(offsetTodayQty);/*可平今 撤单数量*/
            buyPosition.setFreezeQty(frozenQty);/*冻结持仓量*/
            /**持仓额*/
            buyPosition.setAmount(amt);
            /**保证金*/
            buyPosition.setMargin( margin);
            /**平仓盈亏*/
            buyPosition.setGdAmount(profitFund);
            /**持仓盈亏*/
            buyPosition.setFdAmount(floatProfitLoss);
            /**持仓均价*/
            buyPosition.setAvgPrice( avePrice);
            /**最新价*/
            buyPosition.setLastPrice(lastPrice);
            /**交易目的*/
            buyPosition.setTradePurpose( tradePurpose);
            rspTotalPosition.setBuyPos(buyPosition);
        }else {
            /**卖方持仓*/
            Positions sellPosition = new Positions();
            sellPosition.setQutantity(qty); //可用持仓量
            sellPosition.setOffsetQty(offsetQty);//可平量 成交量
            sellPosition.setOffsetLastQty(offsetLastQty);/*可平昨 未成交数量*/
            sellPosition.setOffsetTodayQty(offsetTodayQty);/*可平今 撤单数量*/
            sellPosition.setFreezeQty(BigDecimal.ZERO);/*冻结持仓量*/
            /**持仓额*/
            sellPosition.setAmount(amt);
            /**保证金*/
            sellPosition.setMargin( margin);
            /**平仓盈亏*/
            sellPosition.setGdAmount(profitFund);
            /**持仓盈亏*/
            sellPosition.setFdAmount(floatProfitLoss);
            /**持仓均价*/
            sellPosition.setAvgPrice( avePrice);
            /**最新价*/
            sellPosition.setLastPrice(lastPrice);
            /**交易目的*/
            sellPosition.setTradePurpose( tradePurpose);
            rspTotalPosition.setSellPos(sellPosition);
        }
        return rspTotalPosition;
    }

    public TotalPosition initTotalPostion(ContractInfoBasic contactInfo,String account,String traderNo){
        /**交易品种*/
        String symbol=contactInfo.getSymbol();
        /**拼接key值=账户号+会员号+合约号*/
        String key=account+symbol;

        /**new缓存的持仓信息*/
        TotalPosition totalPosition =new TotalPosition();
        /**交易员号*/
        totalPosition.setTraderNo(traderNo);
        /**交易员号*/
        totalPosition.setAccount(account);
        /**合约号*/
        totalPosition.setSymbol(symbol);
        /**合约信息*/
        totalPosition.setContractInfo(contactInfo);
        /**return*/
        return totalPosition;
    }
}
