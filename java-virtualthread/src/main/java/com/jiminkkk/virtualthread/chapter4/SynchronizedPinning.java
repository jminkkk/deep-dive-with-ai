package com.jiminkkk.virtualthread.chapter4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@code synchronized} 블록 안에서 블로킹 I/O를 수행하면 Virtual Thread가 carrier에 고정(pinning)된다.
 *
 * <p>Pinning이 발생하는 이유:
 * Java 21에서 {@code synchronized}는 OS 모니터(객체 헤더의 lock word)를 사용한다.
 * 모니터는 carrier thread에 묶여 있어서 VT가 블로킹되어도 carrier를 반납할 수 없다.
 * ({@code ReentrantLock}은 Java 객체 기반으로 VT가 park()로 carrier를 반납할 수 있다.)
 *
 * <p>Pinning 발생 시 결과:
 * <ol>
 *   <li>블로킹된 VT가 carrier thread를 독점한다</li>
 *   <li>다른 VT가 해당 carrier를 사용하지 못한다</li>
 *   <li>carrier 수를 초과하는 동시 pinning이 발생하면 전체 처리가 지연된다</li>
 * </ol>
 *
 * <p>Pinning 감지: JVM 옵션 {@code -Djdk.tracePinnedThreads=short} 또는
 * {@code -Djdk.tracePinnedThreads=full}로 실행하면 pinning 발생 시 스택 트레이스를 출력한다.
 * (이 프로젝트의 build.gradle에 기본 설정되어 있다.)
 *
 * <p>주의: Java 24 (JEP 491)에서 {@code synchronized}의 pinning이 해결되었다.
 * 이 클래스는 Java 21 동작을 기준으로 한다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Pinning">JEP 444: Pinning</a>
 */
public class SynchronizedPinning {

    /**
     * 각 작업이 자체 락을 가지고 blocking하는 경우 — pinning만 발생, 락 경합 없음.
     * Carrier thread 수보다 많은 동시 작업이 pinned 상태가 되면 처리 지연이 발생한다.
     */
    public long runWithPinning(int taskCount, int ioDelayMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                final Object taskLock = new Object();  // 작업마다 독립된 락 — 락 경합 없음
                executor.submit(() -> {
                    try {
                        synchronized (taskLock) {
                            // ❌ synchronized 블록 안에서 blocking → Pinning!
                            // carrier thread가 이 VT에 고정되어 다른 VT가 해당 carrier를 사용 불가
                            Thread.sleep(ioDelayMs);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(60, TimeUnit.SECONDS);
        }

        return System.currentTimeMillis() - start;
    }
}
