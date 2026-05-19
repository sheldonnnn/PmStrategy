package com.cmbc.strategy.domain.model.config;
import com.cmbc.strategy.domain.dto.ClientMemberInfo;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.time.LocalTime;

/**
 * 策略配置类
 */
@Data
public class HedgeStrategyConfig extends StrategyConfig {

    private String instanceId;

    // ==========================================
    // 1. 基础信息配置 (Basic Info)
    // ==========================================

    private String strategyId;
    private String tradeTag;
    private String priceBaseType;
    private BigDecimal futureBidSpread;
    private BigDecimal futureOfrSpread;
    private BigDecimal spotMaxOrderQty;
    private BigDecimal orderIntervalSec;
    private BigDecimal maxQtySum;
    private String chaseFlatOrderType;
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
    private BigDecimal spotSellChaseSpread;
    private BigDecimal chaseMaxQtySum;
    private String tradeNode;
    private String exchId;
    private String counterParty;
    private String fxSymbol;

    /**
     * 分时段平盘规则列表
     * 对应关系：一条策略配置 -> 多条时间段规则
     */
    private List<SymbolTimeSlice> symbolTimeSlices;

    private String traderNo;
    private String userId;
    private String account;
    private String tagCode;
    private String tagName;
    private Map<String, ClientMemberInfo> clientMemberInfo;

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