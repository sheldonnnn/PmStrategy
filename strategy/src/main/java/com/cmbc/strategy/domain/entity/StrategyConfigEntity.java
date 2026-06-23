package com.cmbc.strategy.domain.entity;

import com.ctc.wstx.shaded.msv_core.datatype.xsd.datetime.BigDateTimeValueType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class StrategyConfigEntity {
    private String strategyName;
    private String configId;
    private String tradeTag;
    private String orderPriceBase;
    private BigDecimal futureBidSpread;
    private BigDecimal futureOfrSpread;
    private BigDecimal spotMaxOrderQty;
    private BigDecimal orderIntervalSec;
    private BigDecimal maxQtySum;
    private String orderReminder;
    private Integer chaseNumber;
    private BigDecimal chaseOrderDeviation;
    private BigDecimal orderTimeoutSec;
    private BigDecimal hedgingMaxTime;
    private String offsetFlag;
    private String chasePriceType;
    private BigDecimal futureBuyChaseSpread;
    private BigDecimal futureSellChaseSpread;
    private BigDecimal chaseMaxOrderQty;
    private BigDecimal chaseMaxDuration;
    private BigDecimal chaseOrderTimeout;
    private Date createDate;
    private Date updateDate;
    private String updatedUser;
    private String imsWarnName;
    private String imsWarnInfo;
    private String checkStatus;
    private BigDecimal futureMaxOrderQty;
    private BigDecimal xauMaxOrderQty;
    private BigDecimal chaseFutureMaxOrderQty;
    private BigDecimal chaseSpotMaxOrderQty;
    private BigDecimal chaseXauMaxOrderQty;
    private BigDecimal spotOfrSpread;
    private BigDecimal spotBidSpread;
    private BigDecimal spotBuyChaseSpread;
    private BigDecimal spotSellChaseSpread;
    private BigDecimal chaseMaxQtySum;
    private String tradeMode;
    private BigDecimal sgeLimitBuffer;
    private BigDecimal shfeLimitBuffer;
    private String isChase; //是否追单
    private BigDecimal maxSpread;// 境外合约买卖最大点差
}
