package com.jiminkkk.forkjoin.chapter2;

import com.jiminkkk.forkjoin.core.WorkerThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkerThread의 핵심 동작을 검증한다.
 *
 * <p>학습 포인트:
 * <ol>
 *   <li>워커는 자신의 큐에서 먼저 태스크를 꺼낸다</li>
 *   <li>큐가 비면 다른 워커에서 steal한다</li>
 * </ol>
 */
class WorkerThreadTest {

    private List<WorkerThread> workers;

    @BeforeEach
    void setUp() {
        workers = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        workers.forEach(WorkerThread::stopWorker);
    }

    @Test
    @DisplayName("단일 워커가 자신의 큐에 있는 태스크를 모두 처리해야 한다")
    void singleWorker_ProcessesAllSubmittedTasks() throws InterruptedException {
        // Given
        WorkerThread worker = new WorkerThread(0, workers);
        workers.add(worker);

        int taskCount = 10;
        CountDownLatch done = new CountDownLatch(taskCount);
        AtomicInteger processed = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            worker.submit(() -> {
                processed.incrementAndGet();
                done.countDown();
            });
        }

        // When
        worker.start();
        done.await();

        // Then
        assertThat(processed.get()).isEqualTo(taskCount);
    }

    @Test
    @DisplayName("한 워커에 태스크를 몰아주면 다른 워커가 steal해서 처리해야 한다")
    void workerWithEmptyQueue_StealsFromBusyWorker() throws InterruptedException {
        // Given: 워커 2개, 태스크는 worker-0에만 제출
        WorkerThread worker0 = new WorkerThread(0, workers);
        WorkerThread worker1 = new WorkerThread(1, workers);
        workers.add(worker0);
        workers.add(worker1);

        // 두 워커 먼저 시작 후 태스크 제출 — worker-1이 steal 기회를 얻도록
        worker0.start();
        worker1.start();

        int taskCount = 50;
        CountDownLatch done = new CountDownLatch(taskCount);
        AtomicInteger processed = new AtomicInteger(0);

        // worker-0에 태스크를 몰아서 제출 (worker-1은 비어 있음)
        for (int i = 0; i < taskCount; i++) {
            worker0.submit(() -> {
                processed.incrementAndGet();
                done.countDown();
            });
        }

        // When
        done.await();

        // Then: worker-1이 steal해서 처리한 태스크가 있어야 한다
        assertThat(worker1.getStolenCount())
                .as("worker-1이 worker-0의 큐에서 steal해야 한다")
                .isGreaterThan(0);
        assertThat(processed.get())
                .as("두 워커 합산 처리 수가 전체 태스크 수와 같아야 한다")
                .isEqualTo(taskCount);
    }

}
