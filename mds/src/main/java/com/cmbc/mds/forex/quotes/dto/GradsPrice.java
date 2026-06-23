package com.cmbc.mds.forex.quotes.dto;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class GradsPrice implements Serializable {
    private static final long serialVersionUID = 1L;

    private String level; // 级别 从1开始顺序
    private String bid; // 银行买入价
    private String ask; // 银行卖出价
    private String bid2; // 银行买入价
    private String ask2; // 银行卖出价
    private String mid; // 中间价

    private String bidSize; // bid数量
    private String askSize; // ask数量
    private String bidSize2; // bid数量
    private String askSize2; // ask数量

    private String bidSettlDate;
    private String askSettlDate;
    private String bidCurrency;
    private String askCurrency;
    private String bidCondition;
    private String askCondition;

    private String bidSq; // bid加密串
    private String askSq; // ask加密串
    private String bidEntrySpotRate;
    private String askEntrySpotRate;
    private String bidForwardPoints;
    private String askForwardPoints;
    private String bidSpotRate2; // 远期使用
    private String askSpotRate2; //
    private String bidForwardPoints2; // bid远期点
    private String askForwardPoints2; // ask远期点
    private String bidEntrySwapPoints; // 掉期点
    private String askSwapPoints;

    private String bidEntryOriginator; // 行情来源
    private String askEntryOriginator;
    private String bidEntryId; // 重复组内唯一顺序
    private String askEntryId; // 重复组内唯一顺序

    private String bidUpdateAction;
    private String askUpdateAction;
    private String bidSymbol; // 货币对
    private String askSymbol; // 货币对

    private String bidSecurityType; // 即远掉
    private String askSecurityType; // 即远掉

    private String bidQuoteType; // 0:indicative 1:Tradable(default value)
    private String askQuoteType;

    private String bidExpireTime;
    private String askExpireTime;
}