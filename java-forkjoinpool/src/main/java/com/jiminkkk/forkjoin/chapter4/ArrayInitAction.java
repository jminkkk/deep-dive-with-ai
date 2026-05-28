package com.jiminkkk.forkjoin.chapter4;

import java.util.concurrent.RecursiveAction;
import java.util.function.IntUnaryOperator;

/**
 * 배열 초기화를 병렬로 수행하는 RecursiveAction.
 *
 * <p>각 인덱스 i에 대해 사용자가 제공한 함수 f(i)를 적용한다.
 * 병렬 스트림의 {@code Arrays.parallelSetAll}과 유사한 동작이다.
 *
 * <p>사용 예:
 * <pre>
 *   int[] array = new int[1_000_000];
 *   ForkJoinPool.commonPool().invoke(
 *       new ArrayInitAction(array, i -> i * i)  // 제곱수로 초기화
 *   );
 * </pre>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RecursiveAction.html">RecursiveAction Javadoc (Java SE 21)</a>
 */
public class ArrayInitAction extends RecursiveAction {

    private static final int THRESHOLD = 10_000;

    private final int[] array;
    private final int start;
    private final int end;
    private final IntUnaryOperator generator;

    /**
     * @param array     초기화할 배열
     * @param generator 인덱스를 받아 값을 반환하는 함수
     */
    public ArrayInitAction(int[] array, IntUnaryOperator generator) {
        this(array, 0, array.length, generator);
    }

    public ArrayInitAction(int[] array, int start, int end, IntUnaryOperator generator) {
        this.array = array;
        this.start = start;
        this.end = end;
        this.generator = generator;
    }

    @Override
    protected void compute() {
        if (end - start <= THRESHOLD) {
            for (int i = start; i < end; i++) {
                array[i] = generator.applyAsInt(i);
            }
            return;
        }

        int mid = (start + end) / 2;
        invokeAll(
            new ArrayInitAction(array, start, mid, generator),
            new ArrayInitAction(array, mid, end, generator)
        );
    }
}
