package com.jiminkkk.forkjoin.chapter3;

import java.util.concurrent.RecursiveTask;

/**
 * 피보나치 수열을 fork/join으로 계산하는 RecursiveTask 구현.
 *
 * <p><strong>주의: 이 구현은 교육 목적으로만 사용해야 한다.</strong>
 * 실제로는 메모이제이션이나 반복문이 훨씬 효율적이다.
 * 이 구현은 태스크 분할이 매우 작은 단위(n <= 2)까지 일어나기 때문에
 * 태스크 생성/관리 오버헤드가 계산 비용보다 크다.
 *
 * <p>그럼에도 교육 목적에 유용한 이유:
 * <ul>
 *   <li>fork/join의 재귀적 분할 구조를 직관적으로 보여준다</li>
 *   <li>THRESHOLD로 base case를 제어하는 방법을 실습할 수 있다</li>
 *   <li>병렬화가 항상 빠른 것은 아님을 체험할 수 있다</li>
 * </ul>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RecursiveTask.html">RecursiveTask Javadoc (Java SE 21)</a>
 */
public class FibonacciTask extends RecursiveTask<Long> {

    /**
     * 이 값 이하의 n은 직접 계산한다.
     * 값이 클수록 더 많은 작업이 순차적으로 실행되어 오버헤드가 줄어든다.
     */
    private static final int THRESHOLD = 10;

    private final int n;

    public FibonacciTask(int n) {
        this.n = n;
    }

    @Override
    protected Long compute() {
        if (n <= THRESHOLD) {
            return computeSequentially(n);
        }

        FibonacciTask f1 = new FibonacciTask(n - 1);
        FibonacciTask f2 = new FibonacciTask(n - 2);

        f1.fork();
        long result2 = f2.compute();
        long result1 = f1.join();

        return result1 + result2;
    }

    /** 반복문으로 계산하는 base case. 재귀 호출 없이 O(n) 시간에 계산한다. */
    private long computeSequentially(int n) {
        if (n <= 1) return n;
        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            long tmp = a + b;
            a = b;
            b = tmp;
        }
        return b;
    }
}
