package com.jiminkkk.virtualthread.chapter4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code ReentrantLock}으로 Pinning 없이 Virtual Thread에서 안전하게 동시성을 제어한다.
 *
 * <p>{@code synchronized}와 달리 {@code ReentrantLock}은 Java 객체 기반으로 동작한다.
 * {@code lock.lock()}은 내부적으로 {@code LockSupport.park()}를 사용하므로
 * VT가 블로킹될 때 carrier thread를 반납(unmount)할 수 있다.
 *
 * <p>마이그레이션 규칙:
 * <pre>
 * // ❌ Before (Pinning 발생 — Java 21)
 * synchronized (obj) {
 *     blockingOperation();
 * }
 *
 * // ✅ After (Pinning 없음)
 * lock.lock();
 * try {
 *     blockingOperation();
 * } finally {
 *     lock.unlock();
 * }
 * </pre>
 *
 * @see <a href="https://openjdk.org/jeps/444#Pinning">JEP 444: Pinning</a>
 * @see java.util.concurrent.locks.ReentrantLock
 */
public class ReentrantLockFix {

    /**
     * 각 작업이 자체 ReentrantLock을 가지고 blocking하는 경우 — pinning 없음.
     * 블로킹 시 carrier thread를 반납하므로 모든 VT가 진정한 동시 실행이 가능하다.
     */
    public long runWithoutPinning(int taskCount, int ioDelayMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                final ReentrantLock taskLock = new ReentrantLock();  // 작업마다 독립된 락
                executor.submit(() -> {
                    taskLock.lock();
                    try {
                        // ✅ ReentrantLock 내에서 blocking → Unmount 가능
                        // LockSupport.park() 기반이므로 carrier thread를 다른 VT에 반납
                        Thread.sleep(ioDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        taskLock.unlock();
                        latch.countDown();
                    }
                });
            }
            latch.await(60, TimeUnit.SECONDS);
        }

        return System.currentTimeMillis() - start;
    }
}
