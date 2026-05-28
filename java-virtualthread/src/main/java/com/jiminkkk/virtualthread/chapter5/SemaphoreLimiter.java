package com.jiminkkk.virtualthread.chapter5;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Virtual Thread 환경에서 Semaphore로 외부 리소스 동시 접근을 제한한다.
 *
 * <p>플랫폼 스레드 풀에서는 풀 크기로 동시성을 제한했다:
 * <pre>{@code
 * // ❌ VT 환경에서 이 패턴은 의미 없음 — VT는 풀링하지 않는다
 * Executors.newFixedThreadPool(10)
 * }</pre>
 *
 * <p>Virtual Thread는 풀링하지 않으므로 외부 리소스(DB 커넥션, 외부 API QPS 제한)의
 * 동시 접근을 제어하려면 {@link Semaphore}를 사용한다.
 *
 * <p>VT와 Semaphore의 궁합이 좋은 이유:
 * {@code semaphore.acquire()}에서 블로킹될 때 VT가 carrier를 반납(unmount)한다.
 * 수천 개의 VT가 Semaphore 앞에서 대기해도 carrier thread가 고갈되지 않는다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Semaphores">JEP 444: Semaphores</a>
 */
public class SemaphoreLimiter {

    private final Semaphore semaphore;
    private final AtomicInteger currentConcurrency = new AtomicInteger(0);
    private volatile int maxObservedConcurrency = 0;

    public SemaphoreLimiter(int maxConcurrency) {
        this.semaphore = new Semaphore(maxConcurrency);
    }

    /**
     * Semaphore로 동시 실행 수를 제한하며 작업을 수행한다.
     * 허용 수를 초과하는 VT는 acquire()에서 대기 (carrier 반납, 스택 없이 저비용 대기).
     */
    public String executeWithLimit(String taskId, int workMs) throws InterruptedException {
        semaphore.acquire();  // 허용 수 초과 시 VT가 park() — carrier 반납
        int current = currentConcurrency.incrementAndGet();
        synchronized (this) {
            if (current > maxObservedConcurrency) {
                maxObservedConcurrency = current;
            }
        }
        try {
            Thread.sleep(workMs);  // 외부 리소스 접근 시뮬레이션 (DB, API 등)
            return "done:" + taskId;
        } finally {
            currentConcurrency.decrementAndGet();
            semaphore.release();
        }
    }

    /**
     * @return 테스트 실행 중 관찰된 최대 동시 실행 수
     */
    public int getMaxObservedConcurrency() {
        return maxObservedConcurrency;
    }
}
