package com.cmbc.mds.forex.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    // 1. 全局单例，静态初始化 (最关键的一步)
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 在这里统一配置，所有调用处都生效
        // 例如：忽略 JSON 中存在但 Java 类中不存在的字段 (防止报错)
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // 私有构造，防止外部 new
    private JsonUtils() {}

    /**
     * 将 JSON 转为 List<String> 的快捷方法
     */
    public static List<String> toList(String json) {
        try {
            // 这里使用了 TypeReference，支持泛型
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // 实际项目中建议抛出自定义 RuntimeException 或记录日志
            log.error("JSON反序列化失败 json = {}", json, e);
            return null;
        }
    }

    /**
     * 通用的泛型转换方法 (高级版)
     * 用法: JsonUtils.toObj(json, new TypeReference<Map<String, User>>(){})
     */
    public static <T> T toObj(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * 将对象转换为 JSON 字符串
     * @param obj 待转换对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON序列化失败: {}", obj.getClass().getSimpleName(), e);
            // 序列化失败时降级返回默认的 toString，避免抛出异常影响主流程
            return String.valueOf(obj);
        }
    }
}
