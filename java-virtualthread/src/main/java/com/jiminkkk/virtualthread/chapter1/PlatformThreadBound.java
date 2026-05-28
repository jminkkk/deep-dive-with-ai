package com.jiminkkk.virtualthread.chapter1;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 플랫폼 스레드 풀로 대량의 I/O 바운드 작업을 처리한다.
 *
 * <p>플랫폼 스레드는 OS 스레드와 1:1 대응한다. 각 스레드는 ~1MB 스택을 소비하므로
 * 수천 개 이상 생성하면 메모리 부족(OOM)이 발생한다.
 * 따라서 스레드 풀을 사용해 재사용하지만, 풀 크기가 동시 처리 한계가 된다.
 *
 * <p>I/O 대기 중 스레드가 블로킹되면 그 스레드는 아무 일도 하지 않으면서
 * OS 리소스를 점유한다. 이것이 플랫폼 스레드의 근본적 비효율이다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Motivation">JEP 444: Motivation</a>
 */
public class PlatformThreadBound {

    /** 스레드 풀 크기 — 이 수를 초과하는 작업은 대기 큐에 쌓인다 */
    public static final int THREAD_POOL_SIZE = 200;

    /**
     * 지정된 수만큼 I/O 바운드 작업을 플랫폼 스레드 풀로 처리한다.
     *
     * @param taskCount  실행할 작업 수
     * @param ioDelayMs  각 작업의 I/O 대기 시간 (ms)
     * @return 전체 소요 시간 (ms)
     */
    public long runTasks(int taskCount, int ioDelayMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        long start = System.currentTimeMillis();

        try (ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            for (int i = 0; i < taskCount; i++) {
                pool.submit(() -> {
                    try {
                        simulateIO(ioDelayMs);  // I/O 대기 — 스레드 블로킹 (아무 일도 안 함)
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

    /** DB 조회, HTTP 요청 등 I/O 대기를 시뮬레이션 */
    private void simulateIO(int delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
    }
}
