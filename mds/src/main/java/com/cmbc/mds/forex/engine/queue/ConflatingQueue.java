package com.cmbc.mds.forex.engine.queue;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 覆盖型阻塞队列，只保留最新值，并支持关闭信号优先写入。
 */
public class ConflatingQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    private T value;
    private boolean hasValue = false;
    private boolean closed = false;

    /**
     * 写入新值；队列关闭后拒绝写入。
     */
    public boolean offer(T newValue) {
        lock.lock();
        try {
            if (closed) {
                return false;
            }
            value = newValue;
            hasValue = true;
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭队列并唤醒消费者；关闭信号会覆盖未消费数据。
     */
    public boolean close(T closeValue) {
        lock.lock();
        try {
            if (closed) {
                return false;
            }
            closed = true;
            value = closeValue;
            hasValue = true;
            notEmpty.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断队列是否已经关闭。
     */
    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取最新值；无值时阻塞等待。
     */
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (!hasValue) {
                notEmpty.await();
            }
            T result = value;
            value = null;
            hasValue = false;
            return result;
        } finally {
            lock.unlock();
        }
    }
}
