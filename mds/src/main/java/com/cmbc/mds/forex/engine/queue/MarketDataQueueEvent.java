package com.cmbc.mds.forex.engine.queue;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 行情队列事件，统一承载业务数据和通道控制信号。
 */
public final class MarketDataQueueEvent<T> {

    private final EventType type;
    private final T payload;
    private final CompletableFuture<Void> completion;

    private MarketDataQueueEvent(EventType type, T payload, CompletableFuture<Void> completion) {
        this.type = Objects.requireNonNull(type, "type");
        this.payload = payload;
        this.completion = completion;
    }

    /**
     * 创建业务数据事件。
     */
    public static <T> MarketDataQueueEvent<T> data(T payload) {
        return new MarketDataQueueEvent<>(EventType.DATA, Objects.requireNonNull(payload, "payload"), null);
    }

    /**
     * 创建无需回执的通道关闭事件。
     */
    public static <T> MarketDataQueueEvent<T> close() {
        return new MarketDataQueueEvent<>(EventType.CLOSE, null, null);
    }

    /**
     * 创建携带完成回执的通道关闭事件。
     */
    public static <T> MarketDataQueueEvent<T> close(CompletableFuture<Void> completion) {
        return new MarketDataQueueEvent<>(EventType.CLOSE, null, Objects.requireNonNull(completion, "completion"));
    }

    /**
     * 判断当前事件是否为业务数据。
     */
    public boolean isData() {
        return type == EventType.DATA;
    }

    /**
     * 判断当前事件是否为通道关闭信号。
     */
    public boolean isClose() {
        return type == EventType.CLOSE;
    }

    /**
     * 获取业务数据载荷。
     */
    public T payload() {
        return payload;
    }

    /**
     * 获取可选的完成回执。
     */
    public Optional<CompletableFuture<Void>> completion() {
        return Optional.ofNullable(completion);
    }

    /**
     * 标记关闭事件处理成功。
     */
    public void completeSuccessfully() {
        if (completion != null) {
            completion.complete(null);
        }
    }

    /**
     * 标记关闭事件处理失败。
     */
    public void completeExceptionally(Throwable throwable) {
        if (completion != null) {
            completion.completeExceptionally(throwable);
        }
    }

    /**
     * 队列事件类型。
     */
    public enum EventType {
        DATA,
        CLOSE
    }
}
