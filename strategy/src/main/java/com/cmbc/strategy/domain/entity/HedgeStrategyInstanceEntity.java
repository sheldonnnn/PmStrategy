package com.cmbc.strategy.domain.entity;

import com.cmbc.oms.domain.exposure.dto.StrategyPosition;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @Description: 策略运行实例实体
 * @author: cuijian
 * @date: 2023/10/18 10:52
 */
@Data
public class HedgeStrategyInstanceEntity {

        private String instanceId;      // 策略运行实例id
        private String strategyId;      // 策略模板ID
        private String symbolConfigId;  // 合约配置id
        private String tagCode;         // 交易标签
        private String status;          // 状态
        private String configSnapshot;  // 策略配置快照
        private String symbolSliceSnap; // 平盘合约配置快照

        private String createBy;        // 创建者
        private String updateBy;        // 更新者

        private Date createDate;
        private Date updateDate;

        private String traderNo;        // 交易员
        private String remark;          // 备注
        private String delFlag;         // 删除标志 (0代表存在 1代表删除)

        private BigDecimal hedgedPositionSnap;
        private BigDecimal clientPositionSnap;

        public HedgeStrategyInstanceEntity() {
        }

        public HedgeStrategyInstanceEntity(String instanceId, HedgeStrategyConfig config, StrategyPosition positionSummary) {
                if (StringUtils.isEmpty(instanceId) || config == null) {
                        return;
                }

                this.delFlag = "0";
                this.instanceId = instanceId;
                this.strategyId = config.getStrategyId(); // 策略ID

                List<SymbolTimeSlice> symbolTimeSlices = config.getSymbolTimeSlices();
                if (!CollectionUtils.isEmpty(symbolTimeSlices)) {
                        SymbolTimeSlice symbolTimeSlice = symbolTimeSlices.get(0);
                        this.symbolConfigId = symbolTimeSlice.getGroupId(); // 合约配置ID
                }

                this.tagCode = config.getTagCode();
                this.status = String.valueOf(StrategyStatus.CREATED.getCode()); // 初始化状态

                // 配置对象序列化为 JSON 快照
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                try {
                        String configSnapshotData = objectMapper.writeValueAsString(config);
                        this.configSnapshot = configSnapshotData;
                } catch (JsonProcessingException e) {
                        // 异常情况不做处理，不抛出异常，默认快照为空
                        this.configSnapshot = "{}";
                }

                // 设置创建人和更新人
                this.createBy = StringUtils.isBlank(config.getUserId()) ? "admin" : config.getUserId();
                this.updateBy = StringUtils.isBlank(config.getUserId()) ? "admin" : config.getUserId();

                // 设置时间
                Date now = new Date();
                this.createDate = now;
                this.updateDate = now;

                this.traderNo = config.getTraderNo();
                this.remark = "";

                // 记录持仓快照
                if (positionSummary != null) {
                        this.clientPositionSnap = positionSummary.getHedgedNetPosition();
                        this.hedgedPositionSnap = positionSummary.getMgapNetPosition();
                }
        }
}
