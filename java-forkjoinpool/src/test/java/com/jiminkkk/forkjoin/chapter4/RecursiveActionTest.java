package com.jiminkkk.forkjoin.chapter4;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RecursiveAction 구현들의 정확성을 검증한다.
 *
 * RecursiveAction은 반환값 없이 배열을 in-place로 수정한다.
 * 따라서 "결과 반환"이 아닌 "배열 상태 변화"를 검증한다.
 */
class RecursiveActionTest {

    private ForkJoinPool pool;

    @BeforeEach
    void setUp() {
        pool = new ForkJoinPool(4);
    }

    @AfterEach
    void tearDown() {
        pool.shutdown();
    }

    // ========== MergeSortAction 테스트 ==========

    @Test
    @DisplayName("MergeSortAction: 랜덤 배열을 올바르게 정렬해야 한다")
    void mergeSortAction_SortsRandomArray() {
        // Given
        int[] array = {5, 3, 8, 1, 9, 2, 7, 4, 6};
        int[] expected = array.clone();
        Arrays.sort(expected);

        // When
        pool.invoke(new MergeSortAction(array));

        // Then
        assertThat(array).isEqualTo(expected);
    }

    @Test
    @DisplayName("MergeSortAction: 이미 정렬된 배열도 올바르게 처리해야 한다")
    void mergeSortAction_AlreadySortedArray() {
        int[] array = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int[] expected = array.clone();

        pool.invoke(new MergeSortAction(array));

        assertThat(array).isEqualTo(expected);
    }

    @Test
    @DisplayName("MergeSortAction: 역순 배열을 올바르게 정렬해야 한다")
    void mergeSortAction_ReverseSortedArray() {
        int[] array = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        int[] expected = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        pool.invoke(new MergeSortAction(array));

        assertThat(array).isEqualTo(expected);
    }

    @Test
    @DisplayName("MergeSortAction: 중복 원소를 포함한 배열도 올바르게 정렬해야 한다")
    void mergeSortAction_ArrayWithDuplicates() {
        int[] array = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5};
        int[] expected = array.clone();
        Arrays.sort(expected);

        pool.invoke(new MergeSortAction(array));

        assertThat(array).isEqualTo(expected);
    }

    @Test
    @DisplayName("MergeSortAction: 대규모 배열에서 Arrays.sort와 동일한 결과를 내야 한다")
    void mergeSortAction_LargeArrayMatchesArraysSort() {
        // Given
        int size = 100_000;
        int[] array = new Random(42).ints(size).toArray();
        int[] expected = array.clone();
        Arrays.sort(expected);

        // When
        pool.invoke(new MergeSortAction(array));

        // Then
        assertThat(array).isEqualTo(expected);
    }

    @Test
    @DisplayName("MergeSortAction: 단일 원소 배열도 처리해야 한다")
    void mergeSortAction_SingleElementArray() {
        int[] array = {42};
        pool.invoke(new MergeSortAction(array));
        assertThat(array).containsExactly(42);
    }

    // ========== ArrayInitAction 테스트 ==========

    @Test
    @DisplayName("ArrayInitAction: 각 인덱스에 generator 함수를 적용해야 한다")
    void arrayInitAction_AppliesGeneratorToEachIndex() {
        // Given: i * i (제곱수)로 초기화
        int size = 100;
        int[] array = new int[size];

        // When
        pool.invoke(new ArrayInitAction(array, i -> i * i));

        // Then
        for (int i = 0; i < size; i++) {
            assertThat(array[i]).as("array[%d]", i).isEqualTo(i * i);
        }
    }

    @Test
    @DisplayName("ArrayInitAction: 모든 원소를 같은 값으로 초기화할 수 있어야 한다")
    void arrayInitAction_InitializeAllToConstant() {
        int[] array = new int[1000];
        pool.invoke(new ArrayInitAction(array, i -> 7));

        assertThat(array).containsOnly(7);
    }

    @Test
    @DisplayName("ArrayInitAction: 대규모 배열도 올바르게 초기화해야 한다")
    void arrayInitAction_LargeArray() {
        int size = 1_000_000;
        int[] array = new int[size];
        pool.invoke(new ArrayInitAction(array, i -> i));

        // 샘플 몇 개만 검증 (전체 순회는 느림)
        assertThat(array[0]).isEqualTo(0);
        assertThat(array[size / 2]).isEqualTo(size / 2);
        assertThat(array[size - 1]).isEqualTo(size - 1);
    }
}
