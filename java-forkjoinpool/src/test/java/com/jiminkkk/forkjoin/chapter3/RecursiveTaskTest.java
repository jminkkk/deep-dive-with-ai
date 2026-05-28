package com.jiminkkk.forkjoin.chapter3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RecursiveTask 구현들의 정확성을 검증한다.
 *
 * 핵심 질문: 분할정복 결과가 순차 계산 결과와 동일한가?
 */
class RecursiveTaskTest {

    private ForkJoinPool pool;

    @BeforeEach
    void setUp() {
        pool = new ForkJoinPool(4);
    }

    @AfterEach
    void tearDown() {
        pool.shutdown();
    }

    // ========== SumTask 테스트 ==========

    @Test
    @DisplayName("SumTask: 병렬 합산 결과가 순차 합산과 동일해야 한다")
    void sumTask_ParallelResultMatchesSequential() {
        // Given: 1부터 100,000까지의 배열
        int size = 100_000;
        long[] array = LongStream.rangeClosed(1, size).toArray();
        long expectedSum = (long) size * (size + 1) / 2; // 가우스 공식

        // When
        Long result = pool.invoke(new SumTask(array));

        // Then
        assertThat(result).isEqualTo(expectedSum);
    }

    @Test
    @DisplayName("SumTask: 빈 배열의 합은 0이어야 한다")
    void sumTask_EmptyArrayReturnsZero() {
        long[] array = new long[0];
        Long result = pool.invoke(new SumTask(array));
        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("SumTask: 단일 원소 배열은 해당 원소를 반환해야 한다")
    void sumTask_SingleElementArray() {
        long[] array = {42L};
        Long result = pool.invoke(new SumTask(array));
        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("SumTask: THRESHOLD보다 작은 배열은 분할 없이 직접 계산되어야 한다")
    void sumTask_SmallArrayComputedDirectly() {
        // Given: THRESHOLD(1000)보다 작은 배열
        long[] array = LongStream.rangeClosed(1, 100).toArray();
        long expectedSum = 100 * 101 / 2;

        Long result = pool.invoke(new SumTask(array));

        assertThat(result).isEqualTo(expectedSum);
    }

    @Test
    @DisplayName("SumTask: 음수를 포함한 배열도 올바르게 합산해야 한다")
    void sumTask_ArrayWithNegativeNumbers() {
        long[] array = {-5L, -3L, -1L, 1L, 3L, 5L};
        long expectedSum = 0L;

        Long result = pool.invoke(new SumTask(array));

        assertThat(result).isEqualTo(expectedSum);
    }

    // ========== MaxTask 테스트 ==========

    @Test
    @DisplayName("MaxTask: 병렬 최댓값이 순차 최댓값과 동일해야 한다")
    void maxTask_ParallelResultMatchesSequential() {
        // Given
        long[] array = {3L, 1L, 4L, 1L, 5L, 9L, 2L, 6L, 5L, 3L, 5L};
        long expected = 9L;

        Long result = pool.invoke(new MaxTask(array));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("MaxTask: 단일 원소 배열의 최댓값은 그 원소여야 한다")
    void maxTask_SingleElementArray() {
        long[] array = {99L};
        Long result = pool.invoke(new MaxTask(array));
        assertThat(result).isEqualTo(99L);
    }

    @Test
    @DisplayName("MaxTask: 모든 원소가 같을 때 해당 값을 반환해야 한다")
    void maxTask_AllElementsEqual() {
        long[] array = {7L, 7L, 7L, 7L, 7L};
        Long result = pool.invoke(new MaxTask(array));
        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("MaxTask: 대규모 배열에서도 정확한 최댓값을 반환해야 한다")
    void maxTask_LargeArray() {
        int size = 500_000;
        long[] array = new long[size];
        for (int i = 0; i < size; i++) {
            array[i] = i;
        }
        array[size / 3] = Long.MAX_VALUE; // 중간 어딘가에 최댓값 삽입

        Long result = pool.invoke(new MaxTask(array));

        assertThat(result).isEqualTo(Long.MAX_VALUE);
    }

    // ========== FibonacciTask 테스트 ==========

    @Test
    @DisplayName("FibonacciTask: 알려진 피보나치 수를 정확히 계산해야 한다")
    void fibonacciTask_KnownValues() {
        // 피보나치 수열: 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, ...
        int[][] cases = {{0, 0}, {1, 1}, {2, 1}, {5, 5}, {10, 55}, {15, 610}, {20, 6765}};

        for (int[] testCase : cases) {
            Long result = pool.invoke(new FibonacciTask(testCase[0]));
            assertThat(result)
                .as("fib(%d)", testCase[0])
                .isEqualTo((long) testCase[1]);
        }
    }

    @Test
    @DisplayName("FibonacciTask: 점화식 f(n) = f(n-1) + f(n-2)를 만족해야 한다")
    void fibonacciTask_RecurrenceRelationHolds() {
        int n = 25;
        Long fn = pool.invoke(new FibonacciTask(n));
        Long fn1 = pool.invoke(new FibonacciTask(n - 1));
        Long fn2 = pool.invoke(new FibonacciTask(n - 2));

        assertThat(fn).isEqualTo(fn1 + fn2);
    }
}
