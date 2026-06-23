package com.cmbc.strategy.domain.entity;

import com.alibaba.fastjson.JSONObject;
import com.cmbc.oms.domain.exposure.model.HedgePositionSummary;
import com.cmbc.oms.domain.order.model.enums.OrderPriceBaseEnum;
import com.cmbc.strategy.constant.StrategyStatus;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import io.micrometer.common.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @PackageName:
 * @ClassName:ControlRuleFieldInfoCmBean
 * @Description:策略运行实例实体
 * @author:cuijian
 * @date:2023/10/18 10:52
 */
@Data
public class HedgeStrategyInstanceEntity {
    private String instanceId; //策略运行实例id
    private String strategyId; // 策略模版ID
    private String symbolConfigId; // 合约配置id
    private String tagCode; // 交易标签
    private String tagName; // 标签名称
    private String status; // 状态
    private String configSnapshot;// 策略配置快照
    private String symbolSliceSnap; // 平盘合约配置快照
    //规则状态
    private String createBy; // 创建者
    //创建者
    private String updateBy; // 更新者
    //更新者
    private Date createTime;
    private Date updateTime;
    private Date endTime;
    private String traderNo;//交易员
    private String remark; // 备注
    private String delFlag;// 删除标志 (0代表存在 1代表删除)

    private String initialPositionSnap;
    private String finalPositionSnap;
    private BigDecimal cumQty;
    private String strategyName;

    public HedgeStrategyInstanceEntity() {
    }

    public HedgeStrategyInstanceEntity(String instanceId, HedgeStrategyConfig config, HedgePositionSummary positionSummary) {
        if (StringUtils.isEmpty(instanceId) || config == null) {
            return;
        }
        this.delFlag = "0";
        this.instanceId = instanceId;
        this.strategyId = config.getConfigId();// 策略ID
        List<SymbolTimeSlice> symbolTimeSlices = config.getSymbolTimeSlices();
        if (!CollectionUtils.isEmpty(symbolTimeSlices)) {
            SymbolTimeSlice symbolTimeSlice = symbolTimeSlices.get(0);
            this.symbolConfigId = symbolTimeSlice.getGroupId();// 合约配置ID
        }

        this.tagCode = config.getTagCode();
        this.status = StrategyStatus.CREATED.getCode();// 初始化
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            String configSnapshotData = objectMapper.writeValueAsString(config);
            this.configSnapshot = configSnapshotData;
        } catch (JsonProcessingException e) {
            // 异常情况不做处理，不抛出异常，默认快照为空
            this.configSnapshot = "{}";
        }
        this.createBy = config.getUserId();
        this.updateBy = config.getUserId();
        Date now = new Date();
        this.createTime = now;
        this.updateTime = now;
        this.traderNo = config.getTraderNo();
        this.remark = "";
        this.strategyName = OrderPriceBaseEnum.fromTradeCode(config.getTradeMode()) + "-" + config.getStrategyName();
        this.setInitialPositionSnapshot(positionSummary);

    }

    public void setInitialPositionSnapshot(HedgePositionSummary positionSummary) {
        if(positionSummary != null){
            this.initialPositionSnap = JSONObject.toJSONString(positionSummary);
        }
    }

    public void setFinalPositionSnapshot(HedgePositionSummary positionSummary) {
        if(positionSummary != null){
            this.finalPositionSnap = JSONObject.toJSONString(positionSummary);
        }
    }

    public HedgeStrategyConfig getConfigSnapshotByJson() {
        if(StringUtils.isBlank(this.configSnapshot) || "{}".equals(configSnapshot)){
            return new HedgeStrategyConfig();
        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try {
            return objectMapper.readValue(configSnapshot, HedgeStrategyConfig.class);
        } catch (Exception e) {
            return new HedgeStrategyConfig();
        }
    }
}
