package com.jiminkkk.forkjoin.chapter6;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * I/O 블로킹을 시뮬레이션하는 {@link ForkJoinPool.ManagedBlocker} 구현.
 *
 * <p>ForkJoinPool 워커 스레드가 I/O처럼 시간이 걸리는 작업을 기다릴 때,
 * 그냥 블로킹하면 해당 스레드가 사용 불가능해져 병렬성이 떨어진다.
 *
 * <p>ManagedBlocker를 사용하면:
 * <ol>
 *   <li>ForkJoinPool이 스레드가 블로킹될 것을 미리 안다</li>
 *   <li>필요하면 추가 보상 스레드(compensating thread)를 생성한다</li>
 *   <li>목표 병렬성을 유지한다</li>
 * </ol>
 *
 * <p>ManagedBlocker 프로토콜:
 * <pre>
 *   ForkJoinPool.managedBlock(blocker) 실행 시:
 *     while (!blocker.isReleasable()) {
 *         blocker.block();
 *     }
 * </pre>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.ManagedBlocker.html">ForkJoinPool.ManagedBlocker Javadoc (Java SE 21)</a>
 */
public class SimulatedIOBlocker implements ForkJoinPool.ManagedBlocker {

    private final long delayMillis;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private volatile Object result = null;

    /**
     * @param delayMillis 시뮬레이션할 I/O 지연 시간 (밀리초)
     */
    public SimulatedIOBlocker(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    /**
     * 블로킹이 더 이상 필요하지 않으면 true를 반환한다.
     *
     * <p>이 메서드는 논-블로킹이어야 한다.
     * ForkJoinPool은 block()을 호출하기 전에 이 메서드를 먼저 확인한다.
     */
    @Override
    public boolean isReleasable() {
        return completed.get();
    }

    /**
     * 실제 블로킹 작업을 수행한다.
     *
     * <p>이 메서드가 true를 반환하면 isReleasable()도 true를 반환해야 한다.
     * ForkJoinPool은 이 반환값을 보고 루프를 계속할지 결정한다.
     *
     * @return 블로킹이 완료되어 더 이상 기다릴 필요가 없으면 true
     */
    @Override
    public boolean block() throws InterruptedException {
        if (completed.get()) return true;

        // 시뮬레이션: 실제 I/O 대신 sleep으로 지연을 흉내낸다
        TimeUnit.MILLISECONDS.sleep(delayMillis);
        result = "IO_RESULT_" + Thread.currentThread().getName();
        completed.set(true);
        return true;
    }

    /**
     * I/O 작업 결과를 반환한다.
     * block() 완료 전에 호출하면 null이 반환된다.
     */
    public Object getResult() {
        return result;
    }

    public boolean isCompleted() {
        return completed.get();
    }
}
