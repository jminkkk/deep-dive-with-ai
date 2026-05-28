package com.jiminkkk.virtualthread.chapter1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 플랫폼 스레드와 Virtual Thread의 I/O 처리 성능 비교.
 *
 * <p>핵심 질문: 왜 Virtual Thread가 I/O 바운드 작업에 적합한가?
 */
class PlatformVsVirtualThreadTest {

    private static final int TASK_COUNT = 1_000;
    private static final int IO_DELAY_MS = 20;

    @Test
    @DisplayName("플랫폼 스레드: 풀 크기(200)가 작업 수(1000)보다 작으면 여러 배치로 처리된다")
    void platformThreadIsBottleneckOnIO() throws InterruptedException {
        PlatformThreadBound platform = new PlatformThreadBound();

        long elapsed = platform.runTasks(TASK_COUNT, IO_DELAY_MS);

        // 예상: Math.ceil(1000 / 200) × 20ms = 5 × 20ms = 100ms 이상
        long expectedBatches = (long) Math.ceil((double) TASK_COUNT / PlatformThreadBound.THREAD_POOL_SIZE);
        long expectedMinMs = expectedBatches * IO_DELAY_MS;
        System.out.printf("[플랫폼 스레드] %d개 작업, 풀 크기 %d → %dms (예상 최소 %dms)%n",
                TASK_COUNT, PlatformThreadBound.THREAD_POOL_SIZE, elapsed, expectedMinMs);

        assertThat(elapsed).isGreaterThanOrEqualTo(expectedMinMs);
    }

    @Test
    @DisplayName("Virtual Thread: 작업 수(1000)가 많아도 I/O 대기 시간에 가깝게 완료된다")
    void virtualThreadHandlesIOConcurrently() throws InterruptedException {
        VirtualThreadBound virtual = new VirtualThreadBound();

        long elapsed = virtual.runTasks(TASK_COUNT, IO_DELAY_MS);

        // 모든 VT가 동시에 blocking → 거의 단일 작업 시간(20ms) + 오버헤드로 완료
        long toleranceMs = 500;
        System.out.printf("[Virtual Thread] %d개 작업 → %dms (I/O %dms + 오버헤드)%n",
                TASK_COUNT, elapsed, IO_DELAY_MS);

        assertThat(elapsed).isLessThan(IO_DELAY_MS + toleranceMs);
    }

    @Test
    @DisplayName("비교: Virtual Thread가 플랫폼 스레드보다 I/O 처리량이 높다")
    void virtualThreadFasterThanPlatformForIO() throws InterruptedException {
        PlatformThreadBound platform = new PlatformThreadBound();
        VirtualThreadBound virtual = new VirtualThreadBound();

        long platformMs = platform.runTasks(TASK_COUNT, IO_DELAY_MS);
        long virtualMs = virtual.runTasks(TASK_COUNT, IO_DELAY_MS);

        System.out.printf("[비교] 플랫폼: %dms vs Virtual: %dms → Virtual이 %.1fx 빠름%n",
                platformMs, virtualMs, (double) platformMs / virtualMs);

        assertThat(virtualMs).isLessThan(platformMs);
    }
}
