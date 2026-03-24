package com.jiminkkk.forkjoin.core;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Work-Stealing 알고리즘의 핵심인 양방향 큐(Deque)의 교육용 구현.
 *
 * <p>실제 ForkJoinPool의 WorkQueue는 Java 내부 클래스로 구현되어 있으며,
 * CAS(Compare-And-Swap) 연산으로 lock-free하게 동작한다.
 * 이 구현은 원리 이해를 위해 synchronized로 단순화했다.
 *
 * <p>핵심 비대칭성:
 * <ul>
 *   <li>소유자 스레드: LIFO로 push/pop (deque의 top)</li>
 *   <li>다른 스레드(도둑): FIFO로 steal (deque의 bottom)</li>
 * </ul>
 *
 * <p>이 비대칭성이 중요한 이유: 소유자는 가장 최근에 만든(가장 세밀한) 태스크부터
 * 처리하고, 도둑은 가장 오래된(가장 큰) 태스크를 훔쳐간다. 따라서 도둑이 훔쳐간
 * 태스크는 다시 분할될 가능성이 높아 풀 전체에 걸쳐 균등하게 부하가 분산된다.
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html">ForkJoinPool Javadoc (Java SE 21)</a>
 * @param <T> 태스크 타입
 */
public class WorkStealingDeque<T> {

    // 교육용 단순화: 실제는 배열 기반 circular buffer + CAS로 구현
    // 실제 ForkJoinPool.WorkQueue는 volatile 배열과 AtomicLong top/base로 동작한다
    private final ArrayDeque<T> deque = new ArrayDeque<>();
    private final AtomicInteger stealCount = new AtomicInteger(0);

    /**
     * 소유자 스레드가 태스크를 상단(top)에 추가한다.
     *
     * <p>실제 구현에서는 top 인덱스를 volatile write로 갱신하며,
     * memory barrier가 다른 스레드에게 가시성을 보장한다.
     */
    public synchronized void push(T task) {
        deque.push(task);
    }

    /**
     * 소유자 스레드가 상단(top)에서 태스크를 꺼낸다 — LIFO.
     *
     * <p>소유자가 자신의 큐에서 꺼낼 때는 잠금 없이 top 인덱스를 감소시키면 된다.
     * 도둑이 접근하는 bottom과 다른 끝이기 때문이다.
     * 단, 큐에 원소가 1개일 때는 도둑과 경쟁이 발생하므로 CAS가 필요하다.
     *
     * @return 꺼낸 태스크, 큐가 비어 있으면 null
     */
    public synchronized T pop() {
        return deque.isEmpty() ? null : deque.pop();
    }

    /**
     * 다른 스레드(도둑)가 하단(bottom)에서 태스크를 훔쳐 간다 — FIFO.
     *
     * <p>실제 구현에서는 base 인덱스에 CAS를 적용하여,
     * 여러 도둑이 동시에 steal을 시도해도 하나만 성공한다.
     *
     * @return 훔친 태스크, 큐가 비어 있으면 null
     */
    public synchronized T steal() {
        if (deque.isEmpty()) return null;
        stealCount.incrementAndGet();
        return deque.pollLast();
    }

    public int size() {
        return deque.size();
    }

    public int getStealCount() {
        return stealCount.get();
    }

    public boolean isEmpty() {
        return deque.isEmpty();
    }
}
