package com.cmbc.strategy.service;

import com.cmbc.strategy.constant.BaseConstant;
import com.cmbc.strategy.dao.ContractInfoDao;
import com.cmbc.strategy.dao.StrategyConfigDao;
import com.cmbc.strategy.dao.SymbolSliceDao;
import com.cmbc.strategy.domain.entity.ContractInfoEntity;
import com.cmbc.strategy.domain.entity.StrategyBaseEntity;
import com.cmbc.strategy.domain.entity.SymbolSliceEntity;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StrategyConfigLoader {


        @Autowired
        private StrategyConfigDao baseDao;

        @Autowired
        private SymbolSliceDao symbolSliceDao;
        @Autowired
        private ContractInfoDao contractInfoDao;

        public HedgeStrategyConfig loadConfig(String baseConfigId, String symbolConfigId) {
            // 1. 查询基础配置表
            StrategyBaseEntity baseEntity = baseDao.selectById(baseConfigId);
            if (baseEntity == null) {
                throw new IllegalArgumentException("基础配置不存在: ID=" + baseConfigId);
            }

            // 2. 查询时间段规则表 (可能有多条记录，通过 GroupID 关联)
            List<SymbolSliceEntity> timeSlicesEntities = symbolSliceDao.selectById(symbolConfigId);
            if (timeSlicesEntities == null || timeSlicesEntities.isEmpty()) {
                throw new IllegalArgumentException("合约时间规则未配置: GroupID=" + symbolSliceDao);
            }
            // 2.1 查询合约乘数
            Map<String,ContractInfoEntity> symbolUnitEntities = contractInfoDao.selectById();
            // 3. 组装最终的 Config 对象
            HedgeStrategyConfig config = new HedgeStrategyConfig();

            // 3.1 复制基础属性 (FolderId, MaxOrderQty 等)
            BeanUtils.copyProperties(baseEntity, config);

            // 3.2 转换并填充时间片规则 (List<TimeSlice>)
            List<SymbolTimeSlice> timeSlices = timeSlicesEntities.stream()
                    .map(timeSlicesEntity -> {
                        SymbolTimeSlice symbolTimeSlice = convertRule(timeSlicesEntity,symbolUnitEntities);
                        return symbolTimeSlice;
                    })
                    .collect(Collectors.toList());

            config.setSymbolTimeSlices(timeSlices);

            // 3.3 校验配置完整性 (Fail-Fast)
            validateConfig(config);

            return config;
        }

        private SymbolTimeSlice convertRule(SymbolSliceEntity entity,Map<String,ContractInfoEntity> contractInfoEntities) {


            return SymbolTimeSlice.builder()
                    .startTime(entity.getStartTime()) // LocalTime
                    .endTime(entity.getEndTime())
                    .symbol(entity.getSymbol())
                    .sources(entity.getSources())
                    .triggerLongPosition(entity.getTriggerLongPosition())
                    .triggerShortPosition(entity.getTriggerShortPosition())
                    .configId(entity.getConfigId())
                    .domesticType(entity.getDomesticType())
                    .endLongPosition(entity.getEndLongPosition())
                    .endShortPosition(entity.getEndShortPosition())
                    .id(entity.getId())
                    .fxSymbol(entity.getFxSymbol())
                    .unit(entity.getSymbol().equals("XAU/USD") ? BaseConstant.KG_UNIT : contractInfoEntities.get(entity.getSymbol()).getUnit())
                    .build();
        }

        private void validateConfig(HedgeStrategyConfig config) {
            // 简单的逻辑校验，例如时间片是否重叠，阈值是否为正数等
            if (config.getConfigId() == null) throw new RuntimeException("ConfigId缺失");
            // ... 其他校验
        }



}
