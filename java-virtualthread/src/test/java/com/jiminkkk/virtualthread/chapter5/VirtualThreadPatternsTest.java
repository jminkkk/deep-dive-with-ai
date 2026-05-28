package com.jiminkkk.virtualthread.chapter5;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Virtual Thread 실전 패턴 학습.
 *
 * <p>핵심 질문:
 * - ThreadLocal을 VT 환경에서 쓰면 왜 문제인가?
 * - 동시성 제한은 어떻게 해야 하는가?
 * - 정말 10만 개 VT가 동시에 실행될 수 있는가?
 */
class VirtualThreadPatternsTest {

    @Test
    @DisplayName("ThreadLocal 함정: VT마다 새 인스턴스 생성 — 캐싱 효과 없음")
    void threadLocalCreatesNewInstancePerVT() throws Exception {
        ThreadLocalTrap trap = new ThreadLocalTrap();
        int taskCount = 100;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                final String val = "value-" + i;
                futures.add(executor.submit(() -> trap.buildWithCachedThreadLocal(val)));
            }
            for (Future<String> f : futures) {
                assertThat(f.get()).startsWith("result:");
            }
        }

        // VT는 재사용되지 않으므로 ThreadLocal이 매번 새로 초기화됨
        long newCount = trap.getNewInstanceCount();
        System.out.printf("[ThreadLocal 함정] %d개 작업 중 %d번 신규 초기화 (캐싱 효과: %.0f%%)%n",
                taskCount, newCount, (1.0 - (double) newCount / taskCount) * 100);

        // 대부분의 작업에서 ThreadLocal이 새로 초기화됨 (캐싱 미스)
        assertThat(newCount).isGreaterThan(taskCount / 2);
    }

    @Test
    @DisplayName("Semaphore: 동시 실행 수를 제한하면서 대기 중 VT는 carrier를 반납한다")
    void semaphoreLimitsConcurrencyWithoutBlockingCarrier() throws Exception {
        int maxConcurrency = 3;
        SemaphoreLimiter limiter = new SemaphoreLimiter(maxConcurrency);
        int taskCount = 20;
        int workMs = 50;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                final String id = "task-" + i;
                futures.add(executor.submit(() -> limiter.executeWithLimit(id, workMs)));
            }
            for (Future<String> f : futures) {
                assertThat(f.get()).startsWith("done:");
            }
        }

        System.out.printf("[Semaphore] 제한 %d개, 작업 %d개 → 최대 동시 실행: %d개%n",
                maxConcurrency, taskCount, limiter.getMaxObservedConcurrency());

        // 동시 실행 수가 제한을 초과하지 않음
        assertThat(limiter.getMaxObservedConcurrency()).isLessThanOrEqualTo(maxConcurrency);
    }

    @Test
    @DisplayName("고동시성: 100,000개 VT 생성 및 완료 (플랫폼 스레드로 불가능한 수)")
    void hundredThousandVirtualThreadsComplete() throws Exception {
        int taskCount = 100_000;
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>(taskCount);
            for (int i = 0; i < taskCount; i++) {
                final int val = i;
                futures.add(executor.submit(() -> val * 2));
            }
            long completed = futures.stream().mapToInt(f -> {
                try { return f.get(); } catch (Exception e) { return -1; }
            }).filter(v -> v >= 0).count();
            assertThat(completed).isEqualTo(taskCount);
        }

        System.out.printf("[고동시성] VT %d개 생성 및 완료: %dms%n",
                taskCount, System.currentTimeMillis() - start);
    }

    @Test
    @DisplayName("Thread-per-request: 요청마다 VT 생성 — 전통적 동기 코드 스타일 유지 가능")
    void threadPerRequestPattern() throws Exception {
        int requestCount = 500;
        int ioDelayMs = 10;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                final int reqId = i;
                futures.add(executor.submit(() -> {
                    // 동기 코드 스타일 그대로 — 비동기 콜백, CompletableFuture 불필요
                    Thread.sleep(ioDelayMs);           // DB 조회
                    Thread.sleep(ioDelayMs);           // 외부 API 호출
                    return "response-" + reqId;
                }));
            }
            for (Future<String> f : futures) {
                assertThat(f.get()).startsWith("response-");
            }
        }

        System.out.printf("[Thread-per-request] %d개 요청 처리 완료 (각 %dms I/O × 2)%n",
                requestCount, ioDelayMs);
    }
}
