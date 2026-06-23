package com.cmbc.mds.forex.provider.service;

import com.cmbc.mds.forex.common.constants.BaseConstants;
import com.cmbc.mds.forex.common.utils.DateAndTimeUtils;
import com.cmbc.mds.forex.provider.dto.BankStateForWebRsp;
import com.cmbc.mds.forex.provider.event.SourceDisconnectedEvent;
import com.cmbc.mds.forex.quotes.cacheservice.CleanQuotesCacheService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.cmbc.mds.forex.common.utils.ServiceNameUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ForeignBankConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ForeignBankConnectionService.class);

    @Autowired
    private CleanQuotesCacheService cleanQuotesCacheService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 心跳超时阈值（秒），超过此时间无心跳/行情报文则标记断线，默认 90 秒 */
    @Value("${provider.heartbeat.timeout-seconds:90}")
    private long heartbeatTimeoutSeconds;

    /** 看门狗检查周期（秒），默认 30 秒 */
    @Value("${provider.heartbeat.check-interval-seconds:30}")
    private long checkIntervalSeconds;

    /** key 标识source 例如：FXALL UBS */
    private final Map<String, BankStateForWebRsp> bankConnectedsCache = new ConcurrentHashMap<>();

    /** 心跳看门狗调度器，持有引用以支持优雅停机 */
    private ScheduledExecutorService heartbeatScheduler;

    /** 优雅停机等待超时（秒），超时后强制中断剩余任务 */
    @Value("${provider.heartbeat.shutdown-timeout-seconds:5}")
    private long shutdownTimeoutSeconds;

    // =========================================================================
    // 初始化：启动看门狗
    // =========================================================================

    @PostConstruct
    public void startHeartbeatWatchdog() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-watchdog");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(
                this::checkAndExpireSources,
                checkIntervalSeconds, checkIntervalSeconds, TimeUnit.SECONDS);
        log.info("心跳看门狗已启动: 超时阈值={}s, 检查周期={}s", heartbeatTimeoutSeconds, checkIntervalSeconds);
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Watchdog] 正在关闭心跳看门狗调度器...");
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                    log.warn("[Watchdog] 看门狗调度器在 {}s 内未能关闭，强制中断", shutdownTimeoutSeconds);
                    heartbeatScheduler.shutdownNow();
                } else {
                    log.info("[Watchdog] 心跳看门狗调度器已正常关闭");
                }
            } catch (InterruptedException e) {
                log.warn("[Watchdog] 等待看门狗关闭时被中断", e);
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // =========================================================================
    // 核心方法：统一刷新最后活跃时间
    // =========================================================================

    /**
     * 统一入口：刷新数据源最后活跃时间，并根据 connectedFlag 更新连接状态。
     * 心跳报文和行情报文均调用此方法，打通"任意报文=数据源活跃"的语义。
     *
     * @param source      价源标识（如 UBS、JPMC、FXALL），已完成前缀转换
     * @param connectedFlag true=连接正常；false=心跳明确通知断开
     */
    public void refreshLastActive(String source, boolean connectedFlag) {
        if (source == null || source.isEmpty()) {
            return;
        }

        // 使用与 isBankConnected 相同的 Key 生成逻辑，确保缓存互通
        String prefixName = ServiceNameUtils.getPrefixServiceName(source);

        BankStateForWebRsp state = bankConnectedsCache.computeIfAbsent(prefixName, k -> new BankStateForWebRsp());

        // 无论连接状态如何，先刷新活跃时间戳（有报文到达=数据源存活）
        state.setLastActiveTimeMs(System.currentTimeMillis());
        state.setBankName(prefixName);
        state.setRspTime(DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND));

        if (connectedFlag) {
            // 状态变更（断开→连接）时记录日志，防止高频行情刷屏
            if (!BaseConstants.STATUS_CONNECTED.equals(state.getConnectState())) {
                log.info("价源连接状态变更/恢复: source={}, old={}, new={}",
                        prefixName, state.getConnectState(), BaseConstants.STATUS_CONNECTED);
            }
            state.setConnectState(BaseConstants.STATUS_CONNECTED);
        } else {
            // 心跳明确通知断开，立即触发断线处理
            log.warn("心跳报文通知断线: source={}", prefixName);
            sourceDisconnected(prefixName, state);
        }
    }

    // =========================================================================
    // 看门狗：定期扫描超时
    // =========================================================================

    /**
     * 看门狗定时任务：扫描所有已注册数据源，超时则标记断开并触发级联缓存清除
     */
    private void checkAndExpireSources() {
        try {
            long timeoutMs = heartbeatTimeoutSeconds * 1000L;
            long now = System.currentTimeMillis();
            bankConnectedsCache.forEach((source, state) -> {
                // 仅对当前"已连接"状态的数据源进行超时判断
                if (BaseConstants.STATUS_CONNECTED.equals(state.getConnectState())
                        && state.getLastActiveTimeMs() > 0
                        && now - state.getLastActiveTimeMs() > timeoutMs) {
                    long elapsedSec = (now - state.getLastActiveTimeMs()) / 1000;
                    log.warn("价源心跳超时，标记断开: source={}, 已超时={}s (阈值={}s)",
                            source, elapsedSec, heartbeatTimeoutSeconds);
                    sourceDisconnected(source, state);
                }
            });
        } catch (Exception e) {
            log.error("看门狗检查异常", e);
        }
    }

    /**
     * 标记数据源断线，并级联清除该数据源的行情缓存（防止下游查到脏数据）
     */
    private void sourceDisconnected(String source, BankStateForWebRsp state) {
        synchronized (state) {
            if (BaseConstants.STATUS_DISCONNECTED.equals(state.getConnectState())) {
                log.debug("[Connection] 数据源已处于断开状态，跳过重复断线事件发布: source={}", source);
                return;
            }
            log.warn("[Connection] 触发断线级联缓存清除: source={}", source);
            state.setConnectState(BaseConstants.STATUS_DISCONNECTED);
            state.setRspTime(DateAndTimeUtils.getFormatTime(DateAndTimeUtils.TIME_FORMATTER_MILLISECOND));
        }
        cleanQuotesCacheService.removeBySource(source);
        eventPublisher.publishEvent(new SourceDisconnectedEvent(this, source));
    }

    // =========================================================================
    // 查询方法
    // =========================================================================

    /**
     * 判断 ForeignBank 报价商是否处于连接状态
     */
    public boolean isBankConnected(String source) {
        if (log.isDebugEnabled()) {
            log.debug("ForeignBank Connection Status, bankConnecteds = {}", bankConnectedsCache.toString());
        }
        // 1. 转换服务名称，获取前缀: UBS-FIX 转 UBS
        String prefixName = ServiceNameUtils.getPrefixServiceName(source);
        boolean connectState = false; // 默认：false

        // 2. 判断外资行的连接状态缓存是否存在
        if (bankConnectedsCache.containsKey(prefixName)) {
            BankStateForWebRsp bankState = bankConnectedsCache.get(prefixName);
            if (bankState != null) {
                String state = bankState.getConnectState();
                // 3. 校验状态："1" 表示连接正常
                if (BaseConstants.STATUS_CONNECTED.equals(state)) {
                    connectState = true;
                }
            }
        }

        // 特殊硬编码逻辑（内部数据源永远视为已连接）
        if (BaseConstants.SERVICE_NAME_CMDS.equals(prefixName) || BaseConstants.SERVICE_NAME_CMBC.equals(prefixName)) {
            connectState = true;
        }
        return connectState;
    }
}
