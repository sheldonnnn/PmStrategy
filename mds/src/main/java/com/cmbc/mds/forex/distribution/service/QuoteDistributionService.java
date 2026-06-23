package com.cmbc.mds.forex.distribution.service;

import com.cmbc.mds.forex.distribution.channel.DistributionChannel;
import com.cmbc.mds.forex.quotes.dto.Depth;
import com.cmbc.mds.forex.quotes.dto.PloyPrices;
import com.cmbc.mds.forex.subscription.core.SubscriptionCoreService;
import com.cmbc.mds.forex.subscription.core.event.TopicActiveEvent;
import com.cmbc.mds.forex.subscription.core.event.TopicInactiveEvent;
import com.cmbc.mds.forex.subscription.core.model.subcontext.SubscriberContext;
import com.cmbc.mds.forex.subscription.core.model.topic.DistributionDataTopic;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;

/**
 * 行情分发服务
 * 职责：根据订阅关系，将行情数据（清洗后Depth 或 聚合后PloyPrices）分发到对应的通道
 * 核心特性：
 * 1. 支持广播（CleanService -> All）和单播（MergeService -> Strategy）
 * 2. 使用二维路由表实现 O(1) 通道查找
 */
@Service
public class QuoteDistributionService {

    private static final Logger log = LoggerFactory.getLogger(QuoteDistributionService.class);

    @Autowired
    private SubscriptionCoreService subscriptionCoreService;

    @Autowired(required = false)
    private List<DistributionChannel> allChannels;

    // 二维静态路由表：Protocol -> (DataType -> List<Channel>)
    // 静态路由表初始化时，会进行冲突检测：要求 Protocol + DataType
    // 空间换时间，避免运行时 instanceof 判断，实现 O(1) 查找通道
    private final Map<DistributionDataTopic.Protocol, Map<Class<?>, List<DistributionChannel>>> fastRoutingTable = new EnumMap<>(
            DistributionDataTopic.Protocol.class);

    // 动态订阅关系表：SourceTopicKey -> Set<DistributionDataTopic>
    // Key 保持为 SourceKey，以支持广播场景
    private final ConcurrentHashMap<String, Set<DistributionDataTopic>> sourceToDistMap = new ConcurrentHashMap<>();

    // 旁路分发专用虚拟线程池，负责将实际的网络推送、策略回调等分发行为剥离主线程
    private final ExecutorService distributionExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** 优雅停机等待超时（秒），超时后强制中断剩余任务 */
    @Value("${provider.distribution.shutdown-timeout-seconds:5}")
    private long shutdownTimeoutSeconds;

    @PostConstruct
    public void init() {
        if (allChannels == null) {
            allChannels = Collections.emptyList();
        }

        // 1. 初始化 Protocol 维度
        for (DistributionDataTopic.Protocol protocol : DistributionDataTopic.Protocol.values()) {
            fastRoutingTable.put(protocol, new HashMap<>());
        }

        // 2. 注册系统已知的数据类型
        List<Class<?>> knownDataTypes = Arrays.asList(Depth.class, PloyPrices.class);

        // 3. 预编译路由表（带冲突检测）
        for (DistributionChannel channel : allChannels) {
            DistributionDataTopic.Protocol protocol = channel.getProtocol();

            if (protocol == null) {
                log.warn("通道 {} 未定义协议，跳过注册", channel.getClass().getSimpleName());
                continue;
            }

            for (Class<?> dataType : knownDataTypes) {
                if (channel.supports(dataType)) {
                    // 获取该协议+数据类型下的通道列表
                    List<DistributionChannel> registeredChannels = fastRoutingTable.get(protocol)
                            .computeIfAbsent(dataType, k -> new ArrayList<>());

                    // 【核心优化】冲突检测：如果列表中已存在通道，说明发生了定义冲突
                    if (!registeredChannels.isEmpty()) {
                        String existingChannelName = registeredChannels.get(0).getClass().getSimpleName();
                        String newChannelName = channel.getClass().getSimpleName();

                        // 抛出严重异常，阻止容器启动
                        throw new IllegalStateException(String.format(
                                "【严重路由冲突】协议 [%s] 下的数据类型 [%s] 存在多个实现类: [%s] 和 [%s]。" +
                                        "为防止错误分发，系统强制要求 (Protocol + DataType) 必须唯一映射到一个通道。" +
                                        "请检查 Channel 实现类的 getProtocol() 返回值或拆分 Protocol 枚举。",
                                protocol, dataType.getSimpleName(), existingChannelName, newChannelName));
                    }

                    registeredChannels.add(channel);
                }
            }
        }

        // 4. 锁定为不可变列表
        for (Map<Class<?>, List<DistributionChannel>> typeMap : fastRoutingTable.values()) {
            typeMap.replaceAll((k, v) -> Collections.unmodifiableList(v));
        }

        log.info("【极速分发路由表】初始化完成，注册通道数量: {}", allChannels.size());
    }

    // [已删除] private DistributionDataTopic.Protocol determineProtocol(...) 方法

    // ---------------------------------------------------------
    // 动态路由维护 (监听 CoreService 事件)
    // ---------------------------------------------------------

    @EventListener
    public void onTopicActive(TopicActiveEvent event) {
        if (event.getTopic() instanceof DistributionDataTopic) {
            DistributionDataTopic dist = (DistributionDataTopic) event.getTopic();
            String sourceKey = dist.getSourceTopic().getTopicKey();
            // 维护 Source -> [Dist1, Dist2...] 的一对多关系
            sourceToDistMap.computeIfAbsent(sourceKey, k -> ConcurrentHashMap.newKeySet()).add(dist);
        }
    }

    @EventListener
    public void onTopicInactive(TopicInactiveEvent event) {
        if (event.getTopic() instanceof DistributionDataTopic) {
            DistributionDataTopic dist = (DistributionDataTopic) event.getTopic();
            String sourceKey = dist.getSourceTopic().getTopicKey();
            Set<DistributionDataTopic> set = sourceToDistMap.get(sourceKey);
            if (set != null) {
                set.remove(dist);
                if (set.isEmpty()) {
                    sourceToDistMap.remove(sourceKey);
                }
            }
        }
    }

    // ---------------------------------------------------------
    // 分发入口
    // ---------------------------------------------------------

    /**
     * 通用分发入口
     *
     * @param sourceKey 数据源Key (例如 "MD:CLEAN:UBS.UBS:EURUSD" or "MD:MERGE:[FXALL.JPMC,UBS.UBS]:EURUSD") 可以是clean行情，也可以是聚合行情
     * @param data      数据对象 (Depth 或 PloyPrices)
     */
    public void distribute(String sourceKey, Object data) {
        if (data == null) {
            return;
        }
        Class<?> dataType = data.getClass();

        // 1. 获取所有订阅了该源的分发主题
        Set<DistributionDataTopic> distTopics = sourceToDistMap.get(sourceKey);
        if (distTopics == null || distTopics.isEmpty()) {
            return;
        }

        // 2. 遍历分发
        for (DistributionDataTopic dist : distTopics) {

            // 3. 极速查表：根据协议和数据类型获取通道 (O(1) 复杂度)
            Map<Class<?>, List<DistributionChannel>> supportedTypeMap = fastRoutingTable.get(dist.getProtocol()); // 这里
            if (supportedTypeMap == null) {
                continue;
            }

            List<DistributionChannel> matchChannels = supportedTypeMap.get(dataType);
            if (matchChannels == null || matchChannels.isEmpty()) {
                continue;
            }

            // 4. 获取订阅者
            List<SubscriberContext> subscribers = subscriptionCoreService.getAllSubscribers(dist.getTopicKey()); // Key
                                                                                                                 // 结构:
                                                                                                                 // "DIST:{PROTOCOL}:{TARGET_ID}:{SOURCE_KEY}"
            if (subscribers.isEmpty()) {
                continue;
            }

            // 5. 执行异步分发，将耗时的网络I/O、外设推送从主处理链路剥离
            for (DistributionChannel channel : matchChannels) {
                distributionExecutor.submit(() -> {
                    try {
                        // dist.getTopicKey() 中包含了 TargetID，通道内部可以解析使用
                        channel.distribute(dist.getTopicKey(), data, subscribers);
                    } catch (Exception e) {
                        log.error("[Distribution] Error in channel {}", channel.getClass().getSimpleName(), e);
                    }
                });
            }
        }
    }

    /**
     * 优雅停机，确保停止时不再接收新任务并释放线程池资源
     * 等待最多 shutdownTimeoutSeconds 秒让飞行中任务完成，超时后强制中断
     */
    @PreDestroy
    public void shutdown() {
        log.info("[Distribution] 正在关闭旁路分发异步线程池...");
        distributionExecutor.shutdown();
        try {
            if (!distributionExecutor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("[Distribution] 线程池在 {}s 内未能正常关闭，强制中断剩余任务", shutdownTimeoutSeconds);
                distributionExecutor.shutdownNow();
            } else {
                log.info("[Distribution] 旁路分发线程池已正常关闭");
            }
        } catch (InterruptedException e) {
            log.warn("[Distribution] 等待线程池关闭时被中断，强制中断剩余任务", e);
            distributionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}