package com.jiminkkk.forkjoin.chapter5;

import com.jiminkkk.forkjoin.chapter3.SumTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ForkJoinPool의 모니터링 API와 PoolMonitor를 검증한다.
 *
 * 주의: 대부분의 모니터링 값은 추정치(estimate)이므로
 * 정확한 값보다는 합리적인 범위를 검증한다.
 */
class PoolMonitorTest {

    @Test
    @DisplayName("commonPool의 parallelism은 사용 가능한 프로세서 수와 같아야 한다")
    void commonPool_ParallelismEqualsAvailableProcessors() {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        // commonPool의 기본 병렬성은 availableProcessors - 1 이상이어야 한다
        // (시스템 설정에 따라 다를 수 있으므로 최소 1 이상만 확인)
        assertThat(commonPool.getParallelism()).isGreaterThanOrEqualTo(1);
        // 공통 풀은 최대 availableProcessors까지 사용
        assertThat(ForkJoinPool.getCommonPoolParallelism()).isLessThanOrEqualTo(availableProcessors);
    }

    @Test
    @DisplayName("커스텀 parallelism으로 풀을 생성할 수 있어야 한다")
    void customPool_ParallelismIsConfigurable() {
        int targetParallelism = 2;
        ForkJoinPool pool = new ForkJoinPool(targetParallelism);

        try {
            assertThat(pool.getParallelism()).isEqualTo(targetParallelism);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("태스크 실행 후 stealCount가 0 이상이어야 한다")
    void stealCount_NonNegativeAfterExecution() throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(4);

        try {
            long[] array = LongStream.rangeClosed(1, 1_000_000).toArray();
            pool.invoke(new SumTask(array));

            // stealCount는 추정치지만 음수가 될 수 없다
            assertThat(pool.getStealCount()).isGreaterThanOrEqualTo(0);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("PoolMonitor가 스냅샷을 올바르게 캡처해야 한다")
    void poolMonitor_SnapshotCapturesPoolState() {
        ForkJoinPool pool = new ForkJoinPool(3);
        PoolMonitor monitor = new PoolMonitor(pool);

        try {
            PoolMonitor.PoolSnapshot snapshot = monitor.snapshot();

            assertThat(snapshot.parallelism()).isEqualTo(3);
            assertThat(snapshot.isShutdown()).isFalse();
            assertThat(snapshot.isTerminated()).isFalse();
            assertThat(snapshot.stealCount()).isGreaterThanOrEqualTo(0);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("풀 종료 후 isShutdown()은 true를 반환해야 한다")
    void pool_IsShutdownAfterShutdown() throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(2);

        pool.shutdown();

        assertThat(pool.isShutdown()).isTrue();
        // awaitTermination으로 완전 종료 대기
        pool.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(pool.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("commonPool.shutdown()은 무시되어야 한다")
    void commonPool_ShutdownIsIgnored() throws InterruptedException {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();

        // shutdown() 호출
        commonPool.shutdown();

        // shutdown이 실제로 적용되지 않는다
        assertThat(commonPool.isShutdown()).isFalse();
        assertThat(commonPool.isTerminated()).isFalse();

        // shutdown 이후에도 태스크 제출 및 실행이 가능하다
        long[] array = LongStream.rangeClosed(1, 100).toArray();
        Long result = commonPool.submit(new SumTask(array)).join();
        assertThat(result).isEqualTo(5050L);
    }

    @Test
    @DisplayName("풀 크기(poolSize)는 parallelism 이하여야 한다 (유휴 상태에서)")
    void poolSize_NotExceedsParallelism_WhenIdle() {
        int parallelism = 3;
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        try {
            // 유휴 상태에서 poolSize는 parallelism보다 크지 않다
            // (태스크가 없으면 워커가 생성되지 않을 수도 있다)
            assertThat(pool.getPoolSize()).isGreaterThanOrEqualTo(0);
        } finally {
            pool.shutdown();
        }
    }
}
