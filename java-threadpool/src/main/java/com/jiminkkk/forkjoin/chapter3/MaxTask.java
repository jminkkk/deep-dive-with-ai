package com.jiminkkk.forkjoin.chapter3;

import java.util.concurrent.RecursiveTask;

/**
 * 배열의 최댓값을 분할정복으로 찾는 RecursiveTask 구현.
 *
 * <p>SumTask와 동일한 분할 전략을 사용한다.
 * 차이점은 combine 단계에서 합산 대신 Math.max를 사용한다는 것이다.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RecursiveTask.html">RecursiveTask Javadoc (Java SE 21)</a>
 */
public class MaxTask extends RecursiveTask<Long> {

    private static final int THRESHOLD = 1000;

    private final long[] array;
    private final int start;
    private final int end;

    public MaxTask(long[] array) {
        this(array, 0, array.length);
    }

    public MaxTask(long[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Long compute() {
        if (end - start <= THRESHOLD) {
            return computeDirectly();
        }

        int mid = (start + end) / 2;
        MaxTask leftTask = new MaxTask(array, start, mid);
        MaxTask rightTask = new MaxTask(array, mid, end);

        leftTask.fork();
        long rightMax = rightTask.compute();
        long leftMax = leftTask.join();

        return Math.max(leftMax, rightMax);
    }

    private long computeDirectly() {
        long max = array[start];
        for (int i = start + 1; i < end; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }
        return max;
    }
}
