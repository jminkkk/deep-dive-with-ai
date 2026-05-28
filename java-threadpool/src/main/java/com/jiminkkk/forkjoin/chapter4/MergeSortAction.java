package com.jiminkkk.forkjoin.chapter4;

import java.util.Arrays;
import java.util.concurrent.RecursiveAction;

/**
 * 병렬 Merge Sort를 구현하는 RecursiveAction.
 *
 * <p>RecursiveAction은 반환값이 없는 RecursiveTask다.
 * 정렬처럼 결과를 반환하지 않고 배열을 in-place로 수정하는 작업에 적합하다.
 *
 * <p>병렬화 전략:
 * <pre>
 *   1. 배열을 절반으로 분할
 *   2. 두 절반을 병렬로 정렬 (invokeAll 사용)
 *   3. 정렬된 두 절반을 merge
 * </pre>
 *
 * <p>invokeAll()은 fork() + join()의 단축이다:
 * {@code invokeAll(left, right)} = {@code left.fork(); right.compute(); left.join()}
 * 두 서브태스크 중 하나는 현재 스레드에서 직접 실행된다.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RecursiveAction.html">RecursiveAction Javadoc (Java SE 21)</a>
 */
public class MergeSortAction extends RecursiveAction {

    private static final int THRESHOLD = 512;

    private final int[] array;
    private final int start;
    private final int end;

    public MergeSortAction(int[] array) {
        this(array, 0, array.length);
    }

    public MergeSortAction(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected void compute() {
        if (end - start <= THRESHOLD) {
            // base case: 충분히 작으면 순차 정렬
            Arrays.sort(array, start, end);
            return;
        }

        int mid = (start + end) / 2;
        MergeSortAction leftAction = new MergeSortAction(array, start, mid);
        MergeSortAction rightAction = new MergeSortAction(array, mid, end);

        // invokeAll: 두 태스크를 병렬 실행하고 둘 다 완료될 때까지 대기
        invokeAll(leftAction, rightAction);

        // 두 정렬된 절반을 merge
        merge(start, mid, end);
    }

    /**
     * 정렬된 두 구간 [start, mid)와 [mid, end)를 하나의 정렬된 구간으로 합친다.
     *
     * <p>임시 배열이 필요하다. 실제 구현에서는 이 임시 배열을 재사용하여
     * GC 압력을 줄이는 최적화를 적용하기도 한다.
     */
    private void merge(int start, int mid, int end) {
        int[] temp = Arrays.copyOfRange(array, start, end);
        int leftLen = mid - start;

        int i = 0;        // temp의 왼쪽 절반 포인터
        int j = leftLen;  // temp의 오른쪽 절반 포인터
        int k = start;    // 원본 배열 포인터

        while (i < leftLen && j < temp.length) {
            if (temp[i] <= temp[j]) {
                array[k++] = temp[i++];
            } else {
                array[k++] = temp[j++];
            }
        }

        while (i < leftLen) {
            array[k++] = temp[i++];
        }

        while (j < temp.length) {
            array[k++] = temp[j++];
        }
    }
}
