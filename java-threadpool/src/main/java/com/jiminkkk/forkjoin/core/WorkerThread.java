package com.jiminkkk.forkjoin.core;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Work-Stealing 워커 스레드의 교육용 구현.
 *
 * <p>실제 ForkJoinPool에서는 {@code ForkJoinWorkerThread}가 이 역할을 한다.
 * 각 워커는 자신의 {@link WorkStealingDeque}를 가지며,
 * 자신의 큐가 비면 다른 워커의 큐에서 태스크를 훔쳐 실행한다.
 *
 * <p>핵심 루프:
 * <pre>
 * while (실행 중) {
 *     task = 내 큐에서 pop()
 *     if (task == null) {
 *         task = 다른 워커 큐에서 steal()  // 랜덤 희생자 선택
 *     }
 *     if (task != null) {
 *         실행(task)
 *     } else {
 *         잠시 대기 (spinning 또는 park)
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html">ForkJoinPool Javadoc (Java SE 21)</a>
 */
public class WorkerThread extends Thread {

    private final int id;
    private final WorkStealingDeque<Runnable> localQueue = new WorkStealingDeque<>();
    private final List<WorkerThread> allWorkers;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Random random = new Random();

    /** 이 워커가 처리한 태스크 수 */
    private int processedCount = 0;
    /** 이 워커가 steal한 횟수 */
    private int stolenCount = 0;

    public WorkerThread(int id, List<WorkerThread> allWorkers) {
        super("ForkJoinWorker-" + id);
        this.id = id;
        this.allWorkers = allWorkers;
        setDaemon(true);
    }

    /**
     * 이 워커의 큐에 태스크를 제출한다.
     * 일반적으로 외부 제출이나 fork() 호출 시 이 메서드가 사용된다.
     */
    public void submit(Runnable task) {
        localQueue.push(task);
    }

    @Override
    public void run() {
        while (running.get()) {
            Runnable task = localQueue.pop();

            if (task == null) {
                // 내 큐가 비어 있음 → 다른 워커에서 steal 시도
                task = trySteal();
            }

            if (task != null) {
                task.run();
                processedCount++;
            } else {
                // 훔칠 태스크도 없음 → 잠시 양보 (실제 구현은 park/unpark 사용)
                Thread.yield();
            }
        }
    }

    /**
     * 큐 크기가 가장 큰 다른 워커의 큐에서 태스크를 훔쳐 온다.
     *
     * <p>교육용 전략: 자신의 큐보다 더 많은 태스크를 가진 워커 중 가장 큰 것을 선택한다.
     * 훔칠 만한 워커가 없으면 null을 반환한다.
     *
     * <p>실제 ForkJoinPool은 랜덤 희생자 선택을 기본으로 하며,
     * 이 교육용 구현처럼 큐 크기 비교는 별도 최적화로 적용하기도 한다.
     */
    private Runnable trySteal() {
        if (allWorkers.size() <= 1) return null;

        // 자신의 큐보다 큰 워커 중 가장 큰 것을 희생자로 선택
        int maxQueueSize = localQueue.size();
        WorkerThread victim = null;
        for (int i = 0; i < allWorkers.size(); i++) {
            WorkerThread candidate = allWorkers.get(i);
            if (candidate != this && candidate.getLocalQueueSize() > maxQueueSize) {
                maxQueueSize = candidate.getLocalQueueSize();
                victim = candidate;
            }
        }

        if (victim == null) return null;

        Runnable stolen = victim.localQueue.steal();
        if (stolen != null) {
            stolenCount++;
        }
        return stolen;
    }

    public void stopWorker() {
        running.set(false);
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getStolenCount() {
        return stolenCount;
    }

    public int getLocalQueueSize() {
        return localQueue.size();
    }

    public int getWorkerId() {
        return id;
    }
}
