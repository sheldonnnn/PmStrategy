package com.cmbc.strategy.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 策略委托明细
 *
 * @Author: zm
 * @Date: Created on 20190808 14:56.
 */
@Data
public class HedgePositionVo {

    /**
     * [标黄] 多头平仓触发线 (达到此敞口值触发买入平盘)
     * DB: TRIGGER_LONG_POSITION (NUMBER 20,3)
     */
    private BigDecimal triggerLongPosition;

    /**
     * [标黄] 多头平仓终止线 (平盘至此敞口值停止)
     * DB: END_LONG_POSITION (NUMBER 20,3)
     */
    private BigDecimal endLongPosition;

    /**
     * [标黄] 空头平仓触发线 (达到此敞口值触发卖出平盘)
     * DB: TRIGGER_SHORT_POSITION (NUMBER 20,3)
     */
    private BigDecimal triggerShortPosition;

    /**
     * [标黄] 空头平仓终止线 (平盘至此敞口值停止)
     * DB: END_SHORT_POSITION (NUMBER 20,3)
     */
    private BigDecimal endShortPosition;


    // 积存金刻盘头寸
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal goldClientPosition;

    // 积存金刻盘头寸更新时间
    private String goldClientPositionTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal clientPosition;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal hedgedPosition;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal activeExposure;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal netPosition;
    //平仓触发线
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal hedgeTriggerPosition;

    //平仓终止线
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal hedgeEndPosition;

}
