package com.jiminkkk.virtualthread.chapter1;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Virtual Thread로 대량의 I/O 바운드 작업을 처리한다.
 *
 * <p>Virtual Thread는 I/O 블로킹 시 carrier thread에서 언마운트(unmount)된다.
 * 덕분에 소수의 carrier thread 위에서 수십만 개의 Virtual Thread가 동시에 실행될 수 있다.
 * 스레드 풀 크기 제한이 필요 없다 — 작업마다 새 Virtual Thread를 생성해도 된다.
 *
 * <p>Carrier thread는 ForkJoinPool 기반으로, 기본 크기는 CPU 코어 수다.
 * Virtual Thread가 블로킹되면 carrier를 반납하고, 다른 Virtual Thread가 그 carrier를 사용한다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Scheduling">JEP 444: Scheduling</a>
 * @see java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()
 */
public class VirtualThreadBound {

    /**
     * 지정된 수만큼 I/O 바운드 작업을 Virtual Thread로 처리한다.
     * 풀 크기 제한 없음 — 작업마다 새 Virtual Thread를 생성한다.
     *
     * @param taskCount  실행할 작업 수
     * @param ioDelayMs  각 작업의 I/O 대기 시간 (ms)
     * @return 전체 소요 시간 (ms)
     */
    public long runTasks(int taskCount, int ioDelayMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        simulateIO(ioDelayMs);  // 블로킹 → Virtual Thread가 carrier에서 언마운트됨
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
        }

        return System.currentTimeMillis() - start;
    }

    private void simulateIO(int delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
    }
}
