package com.jiminkkk.virtualthread.chapter3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Virtual Thread의 mounting/unmounting 동작 관찰.
 *
 * <p>핵심 질문:
 * - Carrier thread 수보다 많은 VT가 어떻게 동시에 실행될 수 있는가?
 * - blocking 전후에 VT 객체는 어떻게 되는가?
 * - park/unpark가 VT에서 어떻게 동작하는가?
 */
class MountingBehaviorTest {

    @Test
    @DisplayName("Unmounting: carrier 수(CPU 수)보다 많은 VT가 동시에 I/O를 실행한다")
    void moreThanCarrierCountVTsCanRunConcurrently() throws InterruptedException {
        MountingObserver observer = new MountingObserver();
        int carrierCount = observer.getDefaultCarrierThreadCount();
        int vtCount = carrierCount * 10;  // carrier의 10배 (코어의 10배)
        int ioDelayMs = 50;

        CountDownLatch latch = new CountDownLatch(vtCount);
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < vtCount; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        observer.observeAroundBlocking(taskId, ioDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[Unmounting] carrier %d개, VT %d개, I/O %dms → 실제 %dms%n",
                carrierCount, vtCount, ioDelayMs, elapsed);

        // carrier thread 수의 배수가 아닌, I/O 딜레이에 가까운 시간에 완료됨
        // (carrier 수 배수 = 플랫폼 스레드처럼 동작하는 경우)
        assertThat(elapsed).isLessThan(ioDelayMs * 3);
    }

    @Test
    @DisplayName("VT 동일성: blocking 전후에도 Thread.currentThread()는 같은 VT 참조를 반환한다")
    void virtualThreadReferenceRemainsAfterBlocking() throws InterruptedException {
        AtomicBoolean sameRef = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> {
            Thread before = Thread.currentThread();
            try {
                Thread.sleep(50);  // 이 시점에서 carrier가 바뀔 수 있음
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Thread after = Thread.currentThread();
            // VT 객체 자체는 동일 — carrier가 바뀌어도 VT의 정체성은 유지됨
            sameRef.set(before == after);
            latch.countDown();
        });

        latch.await();
        assertThat(sameRef.get()).isTrue();
    }

    @Test
    @DisplayName("park/unpark: Continuation 개념 — park()로 중단하고 unpark()로 재개한다")
    void parkAndUnparkSimulatesContinuation() throws InterruptedException {
        ContinuationConcept cont = new ContinuationConcept();
        String[] result = new String[1];
        CountDownLatch doneLatch = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> {
            result[0] = cont.runWithSuspend("hello");
            doneLatch.countDown();
        });

        cont.resume("world");  // VT가 park()에 도달하면 자동으로 unpark
        assertThat(doneLatch.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(result[0]).isEqualTo("hello + world");
        System.out.println("[Continuation] park/unpark 결과: " + result[0]);
    }

    @Test
    @DisplayName("로그 확인: blocking 전후 VT 상태 출력")
    void printObservations() throws InterruptedException {
        MountingObserver observer = new MountingObserver();
        int vtCount = 3;
        CountDownLatch latch = new CountDownLatch(vtCount);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < vtCount; i++) {
                final int id = i;
                executor.submit(() -> {
                    try {
                        observer.observeAroundBlocking(id, 30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        observer.getLog().forEach(System.out::println);
        assertThat(observer.getLog()).hasSize(vtCount * 2);  // 각 VT당 BEFORE + AFTER
    }
}
