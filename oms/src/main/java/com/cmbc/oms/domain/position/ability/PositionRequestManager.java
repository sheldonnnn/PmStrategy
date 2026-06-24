package com.cmbc.oms.domain.position.ability;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author chendaqian
 * @date 2026/3/19
 * @time 10:24
 * @description 解决使用请求唯一ID筛选的问题，可以使用请求标识管理器
 */
@Component
public class PositionRequestManager {

    // 存储初始化请求ID
    private final Set<String> initRequestIds = ConcurrentHashMap.newKeySet();

    // 存储比对请求ID
    private final Set<String> compareRequestIds = ConcurrentHashMap.newKeySet();

    // 初始化请求ID
    public void addInitRequestId(String requestId) {
        if (requestId != null) {
            initRequestIds.add(requestId);
        }
    }

    // 比对请求ID
    public void addCompareRequestId(String requestId) {
        if (requestId != null) {
            compareRequestIds.add(requestId);
        }
    }

    // 检查是否为初始化请求
    public boolean isInitRequest(String requestId) { return initRequestIds.contains(requestId); }

    // 检查是否为比对请求
    public boolean isCompareRequest(String requestId) {
        return compareRequestIds.contains(requestId);
    }

    // 清除过期请求ID（可选）
    public void cleanupExpiredRequests() {
        // 定期清理过期请求
        initRequestIds.clear();
        compareRequestIds.clear();
    }
}
