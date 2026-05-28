package com.jiminkkk.forkjoin.chapter5;

import java.util.concurrent.ForkJoinPool;

/**
 * ForkJoinPool의 내부 상태를 모니터링하는 유틸리티 클래스.
 *
 * <p>ForkJoinPool은 다양한 모니터링 메서드를 제공한다.
 * 이 클래스는 각 메서드의 의미와 사용 맥락을 실습하기 위해 만들어졌다.
 *
 * <p>주요 모니터링 지표:
 * <ul>
 *   <li>{@code getParallelism()} — 목표 병렬 처리 수준 (CPU 코어 수)</li>
 *   <li>{@code getActiveThreadCount()} — 현재 태스크를 실행 중인 스레드 수 (추정치)</li>
 *   <li>{@code getStealCount()} — 다른 스레드 큐에서 훔쳐온 태스크 수 누적</li>
 *   <li>{@code getQueuedTaskCount()} — 큐에 대기 중인 태스크 수 (추정치)</li>
 *   <li>{@code getPoolSize()} — 시작했으나 종료되지 않은 워커 스레드 수</li>
 * </ul>
 *
 * <p>주의: 대부분의 모니터링 값은 추정치(estimate)다.
 * 멀티스레드 환경에서 정확한 스냅샷을 찍는 것은 비용이 크기 때문이다.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html">ForkJoinPool Javadoc (Java SE 21)</a>
 */
public class PoolMonitor {

    private final ForkJoinPool pool;

    public PoolMonitor(ForkJoinPool pool) {
        this.pool = pool;
    }

    /**
     * 풀의 현재 상태를 스냅샷으로 캡처하여 반환한다.
     */
    public PoolSnapshot snapshot() {
        return new PoolSnapshot(
            pool.getParallelism(),
            pool.getActiveThreadCount(),
            pool.getPoolSize(),
            pool.getStealCount(),
            pool.getQueuedTaskCount(),
            pool.getQueuedSubmissionCount(),
            pool.isShutdown(),
            pool.isTerminated()
        );
    }

    /**
     * 풀 상태를 사람이 읽기 쉬운 형식으로 출력한다.
     */
    public void printStatus(String label) {
        PoolSnapshot snap = snapshot();
        System.out.printf("[%s] parallelism=%d, activeThreads=%d, poolSize=%d, " +
                "stealCount=%d, queuedTasks=%d, queuedSubmissions=%d%n",
            label,
            snap.parallelism(),
            snap.activeThreadCount(),
            snap.poolSize(),
            snap.stealCount(),
            snap.queuedTaskCount(),
            snap.queuedSubmissionCount()
        );
    }

    /**
     * ForkJoinPool 내부 상태의 불변 스냅샷.
     *
     * @param parallelism           목표 병렬 처리 수준
     * @param activeThreadCount     현재 실행 중인 워커 스레드 수 (추정치)
     * @param poolSize              생성된 워커 스레드 수 (종료 전)
     * @param stealCount            work-stealing 횟수 누적 (추정치)
     * @param queuedTaskCount       워커 큐에 대기 중인 태스크 수 (추정치)
     * @param queuedSubmissionCount 아직 실행 시작 못한 제출 태스크 수
     * @param isShutdown            shutdown() 호출 여부
     * @param isTerminated          모든 태스크 완료 후 종료 여부
     */
    public record PoolSnapshot(
        int parallelism,
        int activeThreadCount,
        int poolSize,
        long stealCount,
        long queuedTaskCount,
        int queuedSubmissionCount,
        boolean isShutdown,
        boolean isTerminated
    ) {}
}
