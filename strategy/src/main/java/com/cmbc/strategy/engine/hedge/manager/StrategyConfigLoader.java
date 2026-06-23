package com.cmbc.strategy.engine.hedge.manager;

import com.cmbc.oms.domain.event.ContractInfoBasic;
import com.cmbc.oms.infrastructure.cache.BasicParamCacheManager;
import com.cmbc.strategy.dao.StrategyConfigMapper;
import com.cmbc.strategy.dao.SymbolSliceMapper;
import com.cmbc.strategy.domain.dto.HedgeStrategyRequest;
import com.cmbc.strategy.domain.entity.StrategyConfigEntity;
import com.cmbc.strategy.domain.entity.SymbolSliceEntity;
import com.cmbc.strategy.domain.model.config.HedgeStrategyConfig;
import com.cmbc.strategy.domain.model.config.SymbolTimeSlice;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StrategyConfigLoader {

    @Autowired
    private StrategyConfigMapper strategyConfigMapper;
    @Autowired
    private SymbolSliceMapper symbolSliceMapper;
    @Autowired
    private BasicParamCacheManager basicParamManager;

    public HedgeStrategyConfig loadConfig(HedgeStrategyRequest request) {
        // 1. 查询基础配置表
        StrategyConfigEntity baseEntity = strategyConfigMapper.selectById(request.getStrategyId());
        if (baseEntity == null) {
            throw new IllegalArgumentException("基础配置不存在: strategyId=" + request.getStrategyId());
        }

        // 2. 查询时间段规则表（可能有多个记录，通过 GroupID 关联）
        List<SymbolSliceEntity> timeSlicesEntities = symbolSliceMapper.selectById(request.getSymbolConfigId());
        if (timeSlicesEntities == null || timeSlicesEntities.isEmpty()) {
            throw new IllegalArgumentException("合约时间规则未配置: GroupID=" + request.getSymbolConfigId());
        }

        // 3. 组装最终的 Config 对象
        HedgeStrategyConfig config = new HedgeStrategyConfig();

        // 3.1 复制基础属性 (FolderId, MaxOrderQty 等)
        BeanUtils.copyProperties(baseEntity, config);

        List<SymbolTimeSlice> timeSlices = timeSlicesEntities.stream()
                .map(timeSlicesEntity -> {
                    SymbolTimeSlice symbolTimeSlice = convertRule(timeSlicesEntity);
                    return symbolTimeSlice;
                })
                .collect(Collectors.toList());

        config.setSymbolTimeSlices(timeSlices);

        // 3.3 校验配置完整性 (Fail-Fast)
        validateConfig(config);

        return config;
    }

    private SymbolTimeSlice convertRule(SymbolSliceEntity entity) {
        ContractInfoBasic contractInfo = basicParamManager.getContractInfo(entity.getSymbol());
        String symbol = entity.getSymbol();
        validateSymbolConfig(symbol, contractInfo);
        return SymbolTimeSlice.builder()
                .startTime(entity.getTradeStartTime()) // LocalTime
                .endTime(entity.getTradeEndTime())
                .symbol(entity.getSymbol())
                .triggerLongPosition(entity.getTriggerLongPosition())
                .triggerShortPosition(entity.getTriggerShortPosition())
                .configId(entity.getConfigId())
                .domesticType(contractInfo.getDomesticType()) // 从基础参数中获取
                .endLongPosition(entity.getEndLongPosition())
                .endShortPosition(entity.getEndShortPosition())
                .groupId(entity.getGroupId())
                .fxSymbol(entity.getFxSymbol())
                .unit(contractInfo.getUnit())
                .contractType(contractInfo.getContractType())
                .exchCode(contractInfo.getExchCode())
                .build();
    }

    private void validateConfig(HedgeStrategyConfig config) {
        // 简单的逻辑校验，例如时间片是否重叠，阈值是否为正数等
        if (config.getConfigId() == null) throw new RuntimeException("平盘合约配置ConfigId缺失");
        // ... 其他校验
    }

    private void validateSymbolConfig(String symbol, ContractInfoBasic contractInfo) {
        // 重要数据空值校验
        if (contractInfo == null) throw new RuntimeException("启动策略失败: " + symbol + "合约基础参数配置缺失");
        if (contractInfo.getUnit() == null) throw new RuntimeException("启动策略失败: " + symbol + "合约乘数基础配置缺失");
        if (contractInfo.getDomesticType() == null) throw new RuntimeException("启动策略失败: " + symbol + "合约境内外标识基础配置缺失");
        if (contractInfo.getContractType() == null) throw new RuntimeException("启动策略失败: " + symbol + "合约类型基础配置缺失");
    }
}
