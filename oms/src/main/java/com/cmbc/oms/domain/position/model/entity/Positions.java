package com.cmbc.oms.domain.position.model.entity;

import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.BusinessConstant;
import com.cmbc.oms.infrastructure.util.BasicPositionUtil;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author chendaqian
 * @date 2026/3/28
 * @time 10:24
 * @description 持仓记录
 */
@Data
public class Positions {
    private String InstanceId;//策略实例 可以作为策略建仓/默认全局维度
    private String traderNo;//交易员号
    private String Symbol;//品种
    private String Side;//买卖方向
    private String userName;//前端用户
    private String account;//账户（境内：memberId；境外：报价商）
    private String businessType;//业务类型；境内套利 做市 境内外套利
    /**合约信息*/
    private ContractInfoBasic contractInfo;

    private BigDecimal Qutantity;//累计持仓量
    private BigDecimal freezeQty;//冻结持仓量
    private BigDecimal UseQty;//可用持仓
    private BigDecimal Amount;//持仓金额
    private BigDecimal AvgPrice;//持仓平均价
    private BigDecimal LastPrice;//最新价
    private BigDecimal PLAmount;//持仓盈亏
    private BigDecimal FdAmount;//浮动盈亏
    private BigDecimal GdAmount;//固定盈亏
    private BigDecimal TotalPL;//累计损益
    private BigDecimal SellTradeTotal;//卖出买分总量
    private BigDecimal SellTradeMoneyTotal;//卖出买分总金额
    private BigDecimal BuyTradeTotal;//买入卖分总量
    private BigDecimal BuyTradeMoneyTotal1;//买入卖分总金额
    private BigDecimal margin;             /**保证金*/
    private String tradePurpose;           /**交易目的*/
    private BigDecimal OffsetQty;            /*可平量 成交量*/
    private BigDecimal OffsetLastQty;        /*可平昨 昨日持仓量*/
    private BigDecimal OffsetTodayQty;       /*可平今 持仓量*/
    /**冻结待处理类*/
    private BigDecimal TotalOffsetQty;             /*可平量 成交量*/
    private BigDecimal TotalOffsetLastQty;         /*可平昨 昨日持仓量*/
    private BigDecimal TotalOffsetTodayQty;        /*可平今 持仓量*/

    private BigDecimal todayFreezeQty;//今日冻结持仓量
    private BigDecimal lastdayFreezeQty;//昨日冻结持仓量

    public void init(InitPositions iPos) {
        //初始化部分参数
        this.InstanceId = iPos.getInstanceId();
        this.traderNo = iPos.getTraderNo();
        this.Symbol = iPos.getSymbol();
        this.Side = iPos.getSide();
        this.userName = iPos.getUserName();
        this.account = iPos.getAccount();
        this.businessType = iPos.getBusinessType();

        this.Qutantity = iPos.getQutantity();
        this.freezeQty=BigDecimal.ZERO;
        this.UseQty = iPos.getQutantity().subtract(this.freezeQty);
        this.Amount = iPos.getAmount();
        this.TotalPL = iPos.getTotalPL();
        this.BuyTradeTotal = BigDecimal.ZERO;
        this.SellTradeTotal = BigDecimal.ZERO;
        this.BuyTradeMoneyTotal1 = BigDecimal.ZERO;
        this.SellTradeMoneyTotal = BigDecimal.ZERO;
        /**冻结待处理类*/
        this.OffsetQty = iPos.getOffsetQty();
        this.OffsetLastQty = iPos.getOffsetLastQty();
        this.OffsetTodayQty = iPos.getOffsetTodayQty();
        this.TotalOffsetQty = iPos.getOffsetQty();
        this.TotalOffsetLastQty = iPos.getOffsetLastQty();
        this.TotalOffsetTodayQty = iPos.getOffsetTodayQty();

        this.todayFreezeQty = BigDecimal.ZERO;
        this.lastdayFreezeQty = BigDecimal.ZERO;

        //初始化盈亏等逻辑
        //均价建仓时不计算最新价与均价
        if(this.Qutantity.compareTo(BigDecimal.ZERO)!=0){
            this.AvgPrice = BasicPositionUtil.calcPrice(this.Amount, this.Qutantity,
                    this.contractInfo, BusinessConstant.CAL_TYPE_AVE);
        }else{
            this.AvgPrice=AvgPrice;
        }
    }

    //持仓更新
    public void posUpdate(String bsFlag, String eoFlag, BigDecimal qty, BigDecimal price, String orderState){
        //计算总价等
        BigDecimal totalAmt = BasicPositionUtil.calcPrice(price, qty, this.getContractInfo(), BusinessConstant.CAL_TYPE_TOTAL);
        String inventoryType = this.getContractInfo().getInventoryType();
        //增加或减少持仓 （买卖方向与建仓方向不同为减少持仓：在套利和策略交易方向，比如反向开仓买方向，卖方向仓），需要前端平行建仓逻辑或开平分离逻辑来进行后续处理。
        //只增加持仓，通过开仓或平仓操作与多空双向统计持仓处理。注意多空需要单独做限制。
        if(BusinessConstant.SPOT.equals(inventoryType)){
            spotPosUpdate(bsFlag, qty, price, totalAmt,orderState);
        }else{
            contractPosUpdate(bsFlag, eoFlag, qty, price,totalAmt, orderState);
        }
    }

    /**
     * 现货持仓更新逻辑处理
     */
    private void spotPosUpdate(String bsFlag, BigDecimal qty, BigDecimal price, BigDecimal totalAmt,String orderState) {
        // 判断订单状态（已撤单、已成交等）
        if (BusinessConstant.DEAL.equals(orderState)) {
            // 成交状态
            if (BusinessConstant.BUY_SIDE.equals(bsFlag)) {
                // 买入，处理现货逻辑
                spotPosUpdateForBuy(qty, price, totalAmt,orderState);
            } else {
                // 卖出，处理现货逻辑，需要减去持仓
                spotPosUpdateForSell( qty, price,totalAmt);
            }
        } else if (BusinessConstant.NEW.equals(orderState)) {
            // 新订单状态
            if (BusinessConstant.SELL_SIDE.equals(bsFlag)) {
                // 卖出时，新订单需要冻结持仓
                newOrderFreezePosition( qty);
            }
        } else if (BusinessConstant.CANCEL.equals(orderState)) {
            // 撤单状态
            if (BusinessConstant.SELL_SIDE.equals(bsFlag)) {
                // 卖出撤单时，需要减少冻结
                minusPosition( qty);
            }
        }
    }

    /**
     * 现货买方向业务逻辑
     */
    private void spotPosUpdateForBuy( BigDecimal qty, BigDecimal price, BigDecimal totalAmt,String orderState) {
        if (BusinessConstant.DEAL.equals(orderState)) {
            // 成交，增加持仓
            addPosition( qty);
            // 计算持仓金额和均价，并更新当前买入总额
            calcAvgPrice(totalAmt);
            // 计算浮动盈亏(最新价 - 建仓价 - 平均价)
            calcProfit( price, BusinessConstant.BUY_SIDE);
            // 更新最新交易状态用于成交价
            this.setLastPrice(price);
            //更新总金额
            this.BuyTradeTotal=this.getBuyTradeTotal().add(qty);
            //更新买金额
            this.BuyTradeMoneyTotal1=this.getBuyTradeMoneyTotal1().add(totalAmt)
                    .setScale(BasicPositionUtil.PRECISION, BigDecimal.ROUND_HALF_UP);
        }
    }


    /**
     * 现货卖方向业务逻辑
     */
    private void spotPosUpdateForSell( BigDecimal qty, BigDecimal price,BigDecimal totalAmt) {
        // 成交，减去持仓
        this.setQutantity(this.getQutantity().subtract(qty));
        if (this.getQutantity().compareTo(BigDecimal.ZERO) < 0) {
            this.setQutantity(BigDecimal.ZERO);
        }
        /**冻结处理类*/
        // 减去平今量
        // 减去今日持仓量
        this.setTotalOffsetTodayQty(this.getTotalOffsetTodayQty().subtract(qty));
        if (this.getTotalOffsetTodayQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setTotalOffsetTodayQty(BigDecimal.ZERO);
        }
        //计算新的均价
        calcAvgPrice(totalAmt.multiply(BigDecimal.valueOf(-1)));
        // 更新最新交易状态用于成交价
        this.setLastPrice(price);
        // 计算浮动盈亏 (建仓价 - 最新价 - 平均价)
        calcProfit(price, BusinessConstant.SELL_SIDE);
        // 更新总金额
        this.setSellTradeTotal(this.getSellTradeTotal().add(qty));
        // 更新卖金额
        this.setSellTradeMoneyTotal(this.getSellTradeMoneyTotal()
                .add(totalAmt).setScale(BasicPositionUtil.PRECISION,BigDecimal.ROUND_HALF_UP));

    }

    /**
     * 新订单冻结持仓
     */
    private void newOrderFreezePosition(BigDecimal qty) {
        // 冻结持仓
        this.setFreezeQty(this.getFreezeQty().add(qty));
        // 可用持仓减少
        this.setUseQty(this.getQutantity().subtract(this.getFreezeQty()));
        if (this.getUseQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setUseQty(BigDecimal.ZERO);
        }
        // 可平量 减去冻结
        this.setOffsetQty(this.getOffsetQty().subtract(this.getFreezeQty()));
        if (this.getOffsetQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setOffsetQty(BigDecimal.ZERO);
        }
        // 冻结处理类可平今日持仓量
        this.setTodayFreezeQty(this.getTodayFreezeQty().add(qty));
        // 今日持仓量减去今日冻结量
        this.setOffsetTodayQty(this.getOffsetTodayQty().subtract(this.getFreezeQty()));
        if (this.getOffsetTodayQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setOffsetTodayQty(BigDecimal.ZERO);
        }
    }


    /**
     * 减少冻结
     */
    private void minusPosition(BigDecimal qty) {
        // 减少冻结量
        this.setFreezeQty(this.getFreezeQty().subtract(qty));
        if (this.getFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setFreezeQty(BigDecimal.ZERO);
        }
        // 计算可用数量
        this.setUseQty(this.getQutantity().subtract(this.getFreezeQty()));
        if (this.getUseQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setUseQty(BigDecimal.ZERO);
        }
        //冻结处理类减少平今处理
        this.setTodayFreezeQty(this.getTodayFreezeQty().subtract(qty));
        if (this.getTodayFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setTodayFreezeQty(BigDecimal.ZERO);
        }

        //可平量
        this.setOffsetQty(this.getTotalOffsetQty().subtract(this.getFreezeQty()));
        if (this.getOffsetQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setOffsetQty(BigDecimal.ZERO);
        }
        //可平今日持仓 = 总平量
        this.setOffsetTodayQty(this.getOffsetQty());
    }

    /**
     * 增加持仓
     */
    private void addPosition(BigDecimal qty) {
        // 累计持仓量增加
        this.Qutantity = this.Qutantity.add(qty);
        //计算可用数量
        this.setUseQty(this.getQutantity());
        // 计算可平量
        this.setOffsetQty(this.getOffsetQty().add(qty));
        // 今日持仓量增加
        this.setOffsetTodayQty(this.getOffsetTodayQty().add(qty));
        // 可平量
        this.TotalOffsetQty = this.getTotalOffsetQty().add(qty);
        //今日持仓增加
        this.TotalOffsetTodayQty=this.getTotalOffsetTodayQty().add(qty);
        /**总持仓量增加*/
    }

    /**
     * 期货/通过合约的持仓更新逻辑处理
     */
    private void contractPosUpdate(String bsFlag, String eoFlag, BigDecimal qty, BigDecimal price,BigDecimal totalAmt
            , String orderState) {
        //如果是买和卖对应加或减
        if (this.getSide()!= null && this.getSide().equals(bsFlag) && BusinessConstant.OPEN_POSITION.equals(eoFlag)) {
            // 建仓逻辑
            if (BusinessConstant.DEAL.equals(orderState)) {
                // 成交状态，开仓
                openPosition(bsFlag, qty, price, totalAmt);
            }
        }
        // 撤单与冻结、如果在合并多空双向的方法下方向不一致，只要状态小于等于开仓的进行合仓更新，需要更多限制条件
        else if (BusinessConstant.CLOSE_POSITION.equals(eoFlag) || BusinessConstant.OPEN_POSITION.equals(eoFlag)) {
            // 平仓逻辑
            if (BusinessConstant.NEW.equals(orderState)) {
                // 新订单状态，冻结持仓
                freezePositionForContract(bsFlag, eoFlag, qty);
            } else if (BusinessConstant.DEAL.equals(orderState)) {
                // 成交状态，平仓
                flatPosition(bsFlag, eoFlag, qty, price, totalAmt);
            } else if (BusinessConstant.CANCEL.equals(orderState)) {
                // 撤单状态，解冻持仓
                unfreezePositionForContract(bsFlag, eoFlag, qty);
            }
        }
    }

    /**
     * 开仓处理
     */
    private void openPosition(String bsFlag, BigDecimal qty, BigDecimal price, BigDecimal totalAmt) {
        // 累计持仓增加
        this.setQutantity(this.getQutantity().add(qty));
        // 计算可用持仓
        this.setUseQty(this.getQutantity());
        // 更新买卖总交易
        if (BusinessConstant.SELL_SIDE.equals(bsFlag)) {
            //更新总金额
            this.setSellTradeTotal(this.getSellTradeTotal().add(qty));
            //更新卖金额
            this.setSellTradeMoneyTotal(this.getSellTradeMoneyTotal().add(totalAmt));
        } else if (BusinessConstant.BUY_SIDE.equals(bsFlag)) {
            //更新总金额
            this.setBuyTradeTotal(this.getBuyTradeTotal().add(qty));
            //更新买金额
            this.setBuyTradeMoneyTotal1(this.getBuyTradeMoneyTotal1().add(totalAmt));
        }
    }

    /**
     * 冻结持仓 - 平仓时
     */
    private void freezePositionForContract(String bsFlag, String eoFlag, BigDecimal qty) {
        // 冻结增加
        this.setFreezeQty(this.getFreezeQty().add(qty));

        // 计算可用持仓
        this.setUseQty(this.getQutantity().subtract(this.getFreezeQty()));
        if (this.getUseQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setUseQty(BigDecimal.ZERO);
        }

        // 可平量 减去冻结
        this.setOffsetQty(this.getTotalOffsetQty().subtract(this.getFreezeQty()));

        // 冻结处理处理类
        //如果平昨或平仓("2"、"3")，先冻结昨日持仓如果不够平昨，如果是平今(op="5")，则将处理成平今持仓量和平可量。
        if (BusinessConstant.FLAT_POSITION.equals(eoFlag)) {
            // 昨日冻结持仓量
            this.setLastdayFreezeQty(this.getLastdayFreezeQty().add(qty));
            // 可平昨持仓量
            this.setOffsetLastQty(this.getTotalOffsetLastQty().subtract(this.getLastdayFreezeQty()));
            if (this.getOffsetLastQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setOffsetLastQty(BigDecimal.ZERO);
            }
        } else if (BusinessConstant.FLAT_TODAY_POSITION.equals(eoFlag)) {
            // 今日冻结持仓量
            this.setTodayFreezeQty(this.getTodayFreezeQty().add(qty));
            // 可平今持仓量
            this.setOffsetTodayQty(this.getTotalOffsetTodayQty().subtract(this.getTodayFreezeQty()));
            if (this.getOffsetTodayQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setOffsetTodayQty(BigDecimal.ZERO);
            }
        }
    }

    /**
     * 平仓处理
     */
    private void flatPosition(String bsFlag, String eoFlag, BigDecimal qty, BigDecimal price, BigDecimal totalAmt) {
        // 减去持仓
        this.setQutantity(this.getQutantity().subtract(qty));
        if (this.getQutantity().compareTo(BigDecimal.ZERO) < 0) {
            this.setQutantity(BigDecimal.ZERO);
        }

        // 扣减冻结
        this.setFreezeQty(this.getFreezeQty().subtract(qty));
        if (this.getQutantity().compareTo(BigDecimal.ZERO) < 0) {
            this.setQutantity(BigDecimal.ZERO);
        }

        // 计算可用持仓
        this.setUseQty(this.getQutantity().subtract(this.getFreezeQty()));
        if (this.getUseQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setUseQty(BigDecimal.ZERO);
        }

        // 总持仓减少
        this.setTotalOffsetQty(this.getTotalOffsetQty().subtract(qty));
        if (this.getTotalOffsetQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setTotalOffsetQty(BigDecimal.ZERO);
        }
        // 可平量 减去冻结
        this.setOffsetQty(this.getTotalOffsetQty().subtract(this.getFreezeQty()));

        // 平仓处理类
        //如果平昨或平仓("op=2")，先冻结昨日持仓如果不够平昨，如果是平今(op="5")，则将处理成平今持仓量和平可量。
        if (BusinessConstant.FLAT_POSITION.equals(eoFlag)) {
            //昨日持仓减少
            this.setTotalOffsetLastQty(this.getTotalOffsetLastQty().subtract(qty));
            if (this.getTotalOffsetLastQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setTotalOffsetLastQty(BigDecimal.ZERO);
            }

            // 昨日冻结持仓量扣减
            this.setLastdayFreezeQty(this.getLastdayFreezeQty().subtract(qty));
            if (this.getLastdayFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setLastdayFreezeQty(BigDecimal.ZERO);
            }

            // 可平昨持仓量
            this.setOffsetLastQty(this.getTotalOffsetLastQty().subtract(this.getLastdayFreezeQty()));
            if (this.getOffsetLastQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setOffsetLastQty(BigDecimal.ZERO);
            }
        } else if (BusinessConstant.FLAT_TODAY_POSITION.equals(eoFlag)) {
            // 今日持仓减少
            this.setTotalOffsetTodayQty(this.getTotalOffsetTodayQty().subtract(qty));

            // 今日冻结持仓量扣减
            this.setTodayFreezeQty(this.getTodayFreezeQty().subtract(qty));
            if (this.getTodayFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setTodayFreezeQty(BigDecimal.ZERO);
            }

            // 可平今持仓量
            this.setOffsetTodayQty(this.getTotalOffsetTodayQty().subtract(this.getTodayFreezeQty()));
            if (this.getOffsetTodayQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setOffsetTodayQty(BigDecimal.ZERO);
            }
        }

        // 计算所有待平仓总量、如果先平今后平昨，昨日持仓未扣减完则要补充
        this.setTotalOffsetQty(this.getTotalOffsetQty().subtract(qty));
        if (this.getTotalOffsetQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setTotalOffsetQty(BigDecimal.ZERO);
        }

        //更新新的平均建仓价
        // 最新价等于当前价
        this.setLastPrice(price);
        // 计算浮动盈亏(最新价 - 平均建仓价)
        calcProfit(price, bsFlag);

        // 更新买卖总交易
        if (BusinessConstant.SELL_SIDE.equals(bsFlag)) {
            //更新总金额
            this.setSellTradeTotal(this.getSellTradeTotal().add(qty));
            //更新卖金额
            this.setSellTradeMoneyTotal(this.getSellTradeMoneyTotal().add(totalAmt));
        } else if (BusinessConstant.BUY_SIDE.equals(bsFlag)) {
            //更新总金额
            this.setBuyTradeTotal(this.getBuyTradeTotal().add(qty));
            //更新买金额
            this.setBuyTradeMoneyTotal1(this.getBuyTradeMoneyTotal1().add(totalAmt));
        }
    }

    /**
     * 解冻持仓 - 撤单时
     */
    private void unfreezePositionForContract(String bsFlag, String eoFlag, BigDecimal qty) {
        // 减少冻结
        this.setFreezeQty(this.getFreezeQty().subtract(qty));
        if (this.getFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setFreezeQty(BigDecimal.ZERO);
        }

        // 计算可用持仓
        this.setUseQty(this.getQutantity().subtract(this.getFreezeQty()));
        if (this.getUseQty().compareTo(BigDecimal.ZERO) < 0) {
            this.setUseQty(BigDecimal.ZERO);
        }

        // 可平量 减去冻结
        this.setOffsetQty(this.getTotalOffsetQty().subtract(this.getFreezeQty()));

        // 如果平昨或平仓(op="2")，先解冻昨日持仓如果不够平昨，如果是平今(op="5")，则将处理成平今持仓量和平可量。
        if (BusinessConstant.FLAT_POSITION.equals(eoFlag)) {
            // 昨日冻结持仓量减少
            this.setLastdayFreezeQty(this.getLastdayFreezeQty().subtract(qty));
            if (this.getLastdayFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setLastdayFreezeQty(BigDecimal.ZERO);
            }

            // 可平昨持仓量
            this.setOffsetLastQty(this.getTotalOffsetLastQty().subtract(this.getLastdayFreezeQty()));
            if (this.getOffsetLastQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setOffsetLastQty(BigDecimal.ZERO);
            }

        } else if (BusinessConstant.FLAT_TODAY_POSITION.equals(eoFlag)) {
            // 今日冻结持仓量减少
            this.setTodayFreezeQty(this.getTodayFreezeQty().subtract(qty));
            if (this.getTodayFreezeQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setTodayFreezeQty(BigDecimal.ZERO);
            }

            // 可平今持仓量
            this.setOffsetTodayQty(this.getTotalOffsetTodayQty().subtract(this.getTodayFreezeQty()));
            if (this.getOffsetTodayQty().compareTo(BigDecimal.ZERO) < 0) {
                this.setOffsetTodayQty(BigDecimal.ZERO);
            }
        }
        //重新计算可用值
        this.setAvgPrice(BasicPositionUtil.calcPrice(this.getAmount(),
                this.getQutantity(), this.getContractInfo(), BusinessConstant.CAL_TYPE_AVE));
        this.setAvgPrice(AvgPrice);
    }

    /**
     * 计算盈亏
     */
    public void calcProfit(BigDecimal price, String side) {
        BigDecimal priceSpread;
        if (BusinessConstant.BUY_SIDE.equals(side)) {
            priceSpread = price.subtract(this.getAvgPrice()).setScale(BasicPositionUtil.PRECISION, BigDecimal.ROUND_HALF_UP);
        } else {
            priceSpread = this.getAvgPrice().subtract(price).setScale(BasicPositionUtil.PRECISION, BigDecimal.ROUND_HALF_UP);
        }

        BigDecimal pLAmount = BasicPositionUtil.calcPrice(priceSpread, this.getQutantity(),
                this.getContractInfo(), BusinessConstant.CAL_TYPE_TOTAL);
        this.PLAmount=pLAmount.setScale(BasicPositionUtil.PRECISION, BigDecimal.ROUND_HALF_UP);
    }
    
    private void calcAvgPrice(BigDecimal amount) {
        this.Amount = this.Amount.add(amount);
        this.AvgPrice = BasicPositionUtil.calcPrice(this.Amount, this.Qutantity, this.contractInfo, BusinessConstant.CAL_TYPE_AVE);
    }
}
