package com.cmbc.strategy.domain.model.config;

import com.cmbc.strategy.domain.dto.ClientMemberInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class HedgeStrategyConfig extends StrategyConfig {

    private String instanceId;

    //=========================================|
    // 1. 基础信息配置 (Basic Info)
    //=========================================|

    private String strategyName;
    private String tradeTag;
    private String orderPriceBase;
    private BigDecimal futureBidSpread;
    private BigDecimal futureOfrSpread;
    private BigDecimal spotMaxOrderQty;
    private BigDecimal orderIntervalSec;
    private BigDecimal maxQtySum;
    private Integer chaseNumber;
    private BigDecimal chaseOrderDeviation;
    private BigDecimal orderTimeoutSec;
    private BigDecimal hedgingMaxTime;
    private String chasePriceType;
    private BigDecimal futureBuyChaseSpread;
    private BigDecimal futureSellChaseSpread;
    private BigDecimal chaseMaxDuration;
    private BigDecimal chaseOrderTimeout;
    private String createUser;
    private Date createDate;
    private Date updateDate;
    private String updatedUser;
    private String imsWarnName;
    private String imsWarnInfo;
    private BigDecimal futureMaxOrderQty;
    private BigDecimal xauMaxOrderQty;
    private BigDecimal chaseFutureMaxOrderQty;
    private BigDecimal chaseSpotMaxOrderQty;
    private BigDecimal chaseXauMaxOrderQty;
    private BigDecimal spotOfrSpread;
    private BigDecimal spotBidSpread;
    private BigDecimal spotBuyChaseSpread;
    private BigDecimal chaseMaxQtySum;
    private BigDecimal sgeLimitBuffer; // 上金所涨跌停

    private BigDecimal shfeLimitBuffer; // 上期所涨跌停
    private String tradeMode;
    private String exchId;
    private String counterParty;
    private String fxSymbol;
    private BigDecimal maxSpread;// 境外合约买卖最大点差
    private BigDecimal maxVolume;// 最大交易量

    /**
     * 分时段平盘规则列表
     * 对应关系: 一条策略配置 -> 多条时间段规则
     */
    private List<SymbolTimeSlice> symbolTimeSlices;

    private String traderNo;
    private String userId;
    private String account;
    private String tagCode;
    private String tagName;
    private String isChase; //是否追单
    private Map<String,ClientMemberInfo> clientMemberInfo;

    /**
     * 查找对应合约
     */
    public SymbolTimeSlice findSlice(LocalTime time) {
        if (symbolTimeSlices == null) return null;
        for (SymbolTimeSlice slice : symbolTimeSlices) {
            LocalTime start = slice.getStartTime();
            LocalTime end = slice.getEndTime();
            if (!time.isBefore(start) && time.isBefore(end)) {
                return slice;
            }
        }
        return null;
    }
}
