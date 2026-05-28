package com.jiminkkk.forkjoin.chapter3;

import java.util.concurrent.RecursiveTask;

/**
 * 배열 합산을 분할정복으로 수행하는 RecursiveTask 구현.
 *
 * <p>배열 크기가 THRESHOLD 이하이면 직접 계산(base case),
 * 크면 절반으로 나눠 두 개의 서브태스크로 fork한다.
 *
 * <p>핵심 패턴 — 왼쪽은 fork, 오른쪽은 현재 스레드에서 직접 compute:
 * <pre>
 *   leftTask.fork();                    // 비동기 실행 예약
 *   long rightResult = right.compute(); // 현재 스레드에서 직접 계산 (스레드 낭비 없음)
 *   long leftResult = leftTask.join();  // 왼쪽 완료 대기
 * </pre>
 * 이 패턴은 무조건 fork()만 하는 것보다 효율적이다.
 * 오른쪽을 현재 스레드에서 직접 실행하여 스레드 전환 오버헤드를 줄인다.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RecursiveTask.html">RecursiveTask Javadoc (Java SE 21)</a>
 */
public class SumTask extends RecursiveTask<Long> {

    /** 직접 계산으로 전환하는 임계값. 실험적으로 100~10,000이 적절하다. */
    private static final int THRESHOLD = 1000;

    private final long[] array;
    private final int start;
    private final int end;

    public SumTask(long[] array) {
        this(array, 0, array.length);
    }

    public SumTask(long[] array, int start, int end) {
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
        SumTask leftTask = new SumTask(array, start, mid);
        SumTask rightTask = new SumTask(array, mid, end);

        leftTask.fork();                        // 왼쪽 태스크를 비동기로 실행
        long rightResult = rightTask.compute(); // 오른쪽은 현재 스레드에서 직접 실행
        long leftResult = leftTask.join();      // 왼쪽 완료 대기

        return leftResult + rightResult;
    }

    private long computeDirectly() {
        long sum = 0;
        for (int i = start; i < end; i++) {
            sum += array[i];
        }
        return sum;
    }

    public int getThreshold() {
        return THRESHOLD;
    }
}
