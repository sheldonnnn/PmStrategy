package com.cmbc.mds.forex.provider.service;

import com.cmbc.mds.forex.provider.dto.OmsSourceReq;
import com.cmbc.mds.forex.provider.dto.OrderRejectTypeEvent;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的数据源配置服务
 */
@Service
public class SourceConfigService {

    private static final Logger log = LoggerFactory.getLogger(SourceConfigService.class);

    public static final String STATUS_ENABLED = "1";
    public static final String STATUS_DISABLED = "0";

    // 统一配置缓存：Source Name -> 综合配置信息
    private final Map<String, SourceMetadata> metadataCache = new ConcurrentHashMap<>();

    // 内部类：聚合所有 Source 相关的元数据
    @Data
    public static class SourceMetadata {
        private OmsSourceReq paramConfig;         // 基础状态配置
        private List<String> accounts;              // 绑定的账户列表
        private OrderRejectTypeEvent rejectEvent;   // 拒单状态（若无则为null）
    }

    @PostConstruct
    public void init() {
        // TODO: 留作后续对接数据库或外部配置中心，动态加载数据源参数。
        // 目前为空实现，系统将默认依赖外部触发热更新方法来填充缓存。
        log.info("[SourceConfig] SourceConfigService 初始化完成，等待外部配置加载。");
    }

    /**
     * 检查 source 是否存在且为启用状态
     */
    public boolean isValidSource(String source) {
        SourceMetadata meta = metadataCache.get(source);
        return meta != null 
                && meta.getParamConfig() != null 
                && STATUS_ENABLED.equals(meta.getParamConfig().getStatus());
    }

    /**
     * 检查 source 是否存在拒单事件
     */
    public boolean isSourceRejected(String source) {
        SourceMetadata meta = metadataCache.get(source);
        return meta != null && meta.getRejectEvent() != null;
    }

    /**
     * 获取绑定的账户列表
     */
    public List<String> getSourceAccounts(String source) {
        SourceMetadata meta = metadataCache.get(source);
        return meta != null && meta.getAccounts() != null ? meta.getAccounts() : Collections.emptyList();
    }

    /**
     * 获取数据源的基础配置参数
     */
    public OmsSourceReq getSourceParam(String source) {
        SourceMetadata meta = metadataCache.get(source);
        return meta != null ? meta.getParamConfig() : null;
    }

    /**
     * 更新或设置整个 source 的元数据（提供给外部加载或热更新使用）
     */
    public void updateSourceConfig(String source, SourceMetadata metadata) {
        metadataCache.put(source, metadata);
    }

    /**
     * 获取所有配置了的数据源Key集合
     */
    public List<String> getAllSourceKeys() {
        return new java.util.ArrayList<>(metadataCache.keySet());
    }
}
