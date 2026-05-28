package com.jiminkkk.forkjoin.chapter6;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ManagedBlocker의 핵심 가치를 학습하는 테스트
 *
 * <p>학습 순서:
 * <ol>
 *   <li>SimulatedIOBlocker 상태 머신: 초기 → 블로킹 → 완료</li>
 *   <li>[문제] managedBlock 없이 블로킹 → 풀 쓰레드 고갈 → 데드락</li>
 *   <li>[해결] managedBlock으로 블로킹 → 보상 쓰레드 생성 → 데드락 없음</li>
 *   <li>[효과] 보상 쓰레드 덕분에 쓰레드 수보다 많은 태스크도 병렬 실행됨</li>
 *   <li>block() 멱등성: 이미 완료된 상태에서 재호출해도 안전</li>
 * </ol>
 */
class ManagedBlockerTest {

    // ─── 1. SimulatedIOBlocker 상태 머신 ───────────────────────────────────────

    @Test
    @DisplayName("SimulatedIOBlocker: 초기 상태에서 isReleasable은 false여야 한다")
    void blocker_InitiallyNotReleasable() {
        SimulatedIOBlocker blocker = new SimulatedIOBlocker(100);

        assertThat(blocker.isReleasable()).isFalse();
        assertThat(blocker.isCompleted()).isFalse();
        assertThat(blocker.getResult()).isNull();
    }

    @Test
    @DisplayName("SimulatedIOBlocker: block() 호출 후 isReleasable은 true여야 한다")
    void blocker_ReleasableAfterBlock() throws InterruptedException {
        SimulatedIOBlocker blocker = new SimulatedIOBlocker(50);

        blocker.block();

        // block()이 완료되면 completed=true, isReleasable=true
        // ForkJoinPool은 isReleasable()을 보고 루프를 종료한다
        assertThat(blocker.isReleasable()).isTrue();
        assertThat(blocker.isCompleted()).isTrue();
        assertThat(blocker.getResult()).isNotNull();
    }

    // ─── 2. [문제] managedBlock 없이 블로킹 ────────────────────────────────────

    @Test
    @DisplayName("[문제] ManagedBlocker 없이 블로킹하면 풀 쓰레드가 고갈되어 데드락이 발생한다")
    void withoutManagedBlocker_DeadlockOccursWhenAllThreadsBlocked() throws InterruptedException {
        /*
         * 시나리오:
         *   풀 쓰레드 2개
         *   태스크 A, B: gate를 기다리며 쓰레드 점유 (직접 블로킹)
         *   태스크 C:   gate를 열어줄 태스크
         *
         *   A, B → 쓰레드 2개 모두 점유
         *   C    → 실행할 쓰레드가 없어 큐에서 대기 → 데드락
         *   gate → 영원히 열리지 않음
         */
        int parallelism = 2;
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch allBlocked = new CountDownLatch(parallelism);

        // 태스크 A, B: ManagedBlocker 없이 직접 블로킹 → 보상 쓰레드 생성 안 됨
        for (int i = 0; i < parallelism; i++) {
            pool.execute(() -> {
                try {
                    allBlocked.countDown();
                    gate.await(); // 직접 블로킹: ForkJoinPool에 알리지 않음
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        allBlocked.await(); // A, B 모두 블로킹 상태 확인

        // 태스크 C: gate를 열어줄 태스크 → 쓰레드가 없어서 실행 불가
        AtomicBoolean gateOpened = new AtomicBoolean(false);
        pool.execute(() -> {
            gateOpened.set(true);
            gate.countDown();
        });

        // 300ms 기다려도 C가 실행되지 않음 (데드락)
        Thread.sleep(300);
        assertThat(gateOpened.get())
                .as("쓰레드 고갈로 gate 해제 태스크가 실행되지 못해야 한다")
                .isFalse();

        pool.shutdownNow(); // 강제 종료로 데드락 해제
    }

    // ─── 3. [해결] managedBlock으로 블로킹 ──────────────────────────────────────

    @Test
    @DisplayName("[해결] ManagedBlocker로 블로킹하면 보상 쓰레드가 생성되어 데드락이 발생하지 않는다")
    void withManagedBlocker_CompensationThreadPreventsDeadlock() throws InterruptedException {
        /*
         * 동일한 시나리오에서 ManagedBlocker를 사용하면:
         *   A가 managedBlock 호출 → ForkJoinPool이 "쓰레드가 블로킹될 것"을 인지
         *   → 보상 쓰레드(compensation thread) 생성
         *   → 보상 쓰레드가 C를 실행 → gate 해제
         *   → A, B 모두 완료
         */
        int parallelism = 2;
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch allBlocked = new CountDownLatch(parallelism);
        AtomicInteger completed = new AtomicInteger();

        // 태스크 A, B: ManagedBlocker로 블로킹 → ForkJoinPool이 보상 쓰레드 생성
        for (int i = 0; i < parallelism; i++) {
            pool.execute(() -> {
                try {
                    allBlocked.countDown();
                    ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                        @Override
                        public boolean isReleasable() {
                            return gate.getCount() == 0; // gate가 열렸으면 블로킹 불필요
                        }

                        @Override
                        public boolean block() throws InterruptedException {
                            // 이 메서드 진입 전에 ForkJoinPool이 보상 쓰레드를 생성한다
                            gate.await();
                            return true;
                        }
                    });
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        allBlocked.await(); // A, B 모두 managedBlock 진입 확인

        // 태스크 C: 보상 쓰레드가 실행 → gate 해제 → A, B 완료
        AtomicBoolean gateOpened = new AtomicBoolean(false);
        pool.execute(() -> {
            gateOpened.set(true);
            gate.countDown();
        });

        boolean allCompleted = waitFor(() -> completed.get() == parallelism, 2000);

        assertThat(gateOpened.get()).as("보상 쓰레드가 gate 해제 태스크를 실행해야 한다").isTrue();
        assertThat(allCompleted).as("A, B 모두 완료되어야 한다").isTrue();

        pool.shutdown();
    }

    // ─── 4. [효과] 보상 쓰레드로 병렬 실행 유지 ──────────────────────────────────

    @Test
    @DisplayName("[효과] 쓰레드 수보다 많은 블로킹 태스크도 보상 쓰레드 덕분에 병렬 실행된다")
    void withManagedBlocker_ParallelExecutionDespiteMoreTasksThanThreads() {
        /*
         * 풀 쓰레드 4개, 블로킹 태스크 8개 (각 100ms)
         *
         * managedBlock 없으면: 4개씩 2라운드 → 약 200ms
         * managedBlock 있으면: 보상 쓰레드 생성 → 8개 동시 실행 → 약 100ms
         */
        int parallelism = 4;
        int taskCount = 8;
        long delayMs = 100;
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        @SuppressWarnings("unchecked")
        RecursiveTask<Boolean>[] tasks = new RecursiveTask[taskCount];
        for (int i = 0; i < taskCount; i++) {
            tasks[i] = new RecursiveTask<>() {
                @Override
                protected Boolean compute() {
                    SimulatedIOBlocker blocker = new SimulatedIOBlocker(delayMs);
                    try {
                        ForkJoinPool.managedBlock(blocker);
                        return blocker.isCompleted();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            };
        }

        long start = System.currentTimeMillis();
        for (RecursiveTask<Boolean> task : tasks) {
            pool.execute(task);
        }
        for (RecursiveTask<Boolean> task : tasks) {
            assertThat(task.join()).isTrue();
        }
        long elapsed = System.currentTimeMillis() - start;

        // 순차 실행(2라운드)이면 200ms+
        // 보상 쓰레드로 병렬 실행되면 delayMs + 약간의 오버헤드
        assertThat(elapsed)
                .as("보상 쓰레드로 병렬 실행되어 2라운드(200ms)보다 빨라야 한다. 실제: %dms", elapsed)
                .isLessThan(delayMs * 2 - 20);

        pool.shutdown();
    }

    // ─── 5. block() 멱등성 ────────────────────────────────────────────────────

    @Test
    @DisplayName("block()이 이미 완료된 상태에서 재호출 시 즉시 true를 반환해야 한다")
    void block_IsIdempotentAfterCompletion() throws InterruptedException {
        /*
         * ForkJoinPool.managedBlock()의 내부 루프:
         *   while (!blocker.isReleasable()) { blocker.block(); }
         *
         * block()이 완료 후 재호출되어도 안전해야 한다.
         * 그렇지 않으면 루프에서 중복 블로킹이 발생할 수 있다.
         */
        SimulatedIOBlocker blocker = new SimulatedIOBlocker(10);

        blocker.block(); // 첫 번째: 실제 블로킹 (10ms)
        assertThat(blocker.isCompleted()).isTrue();

        boolean secondResult = blocker.block(); // 두 번째: 즉시 true 반환
        assertThat(secondResult).isTrue();
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /**
     * 조건이 참이 될 때까지 최대 timeoutMs 동안 폴링으로 대기한다.
     *
     * @return 조건이 참이 되면 true, 타임아웃이면 false
     */
    private boolean waitFor(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) return false;
            Thread.sleep(10);
        }
        return true;
    }
}
