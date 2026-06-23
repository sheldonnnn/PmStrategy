package com.cmbc.mds.forex.quotes.adapter;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.utils.ServiceNameUtils;
import com.cmbc.mds.forex.quotes.QuoteRoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuoteAdapterRouter {

    private static final Logger log = LoggerFactory.getLogger(QuoteAdapterRouter.class);

    @Autowired
    private Map<String, QuoteAdapter> adapterMap;

    private final Map<String, QuoteAdapter> routeCache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public void route(Object payload, String source, String provider) {
        route(payload, QuoteRoutingContext.withoutSymbol(source, provider));
    }

    /**
     * 使用 Receiver 预计算好的上下文执行路由，避免 Adapter 入队前重复构建 cleanTopicKey。
     */
    @SuppressWarnings("unchecked")
    public void route(Object payload, QuoteRoutingContext context) {
        String prefixName = ServiceNameUtils.getPrefixServiceName(context.source());
        if (prefixName == null) {
            log.error("无法提取 source 前缀，跳过路由");
            return;
        }

        QuoteAdapter adapter = getAdapter(prefixName);
        if (adapter == null) {
            log.warn("未找到 source [{}] 对应的 Adapter (BeanName={}{})",
                    prefixName, BaseConstants.ADAPTER_BEAN_PREFIX, prefixName.toUpperCase());
            return;
        }

        try {
            adapter.adaptAndHandle(payload, context);
        } catch (ClassCastException e) {
            log.error("Adapter 类型不匹配: source={} 实际传入 {}",
                    prefixName, payload == null ? "null" : payload.getClass().getSimpleName(), e);
        }
    }

    private QuoteAdapter getAdapter(String source) {
        // 注意：每次进入这里都会创建一个新的大写 String 对象，高频调用会增加 GC 压力
        String cacheKey = source.toUpperCase();
        return routeCache.computeIfAbsent(cacheKey, key -> {
            String beanName = BaseConstants.ADAPTER_BEAN_PREFIX + key;
            QuoteAdapter target = adapterMap.get(beanName);
            if (target != null) {
                log.info("Routing: 绑定 source [{}] -> Adapter [{}]", source, beanName);
            }
            return target;
        });
    }
}
