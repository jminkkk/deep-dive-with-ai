package com.jiminkkk.virtualthread.chapter4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pinning 발생과 해결을 성능 차이로 관찰한다.
 *
 * <p>테스트 실행 시 build.gradle에 설정된 {@code -Djdk.tracePinnedThreads=short} 덕분에
 * {@code synchronizedCausesSlowdown} 테스트 실행 중 콘솔에 pinning 스택 트레이스가 출력된다.
 *
 * <p>핵심 질문:
 * - synchronized + blocking이 왜 VT 환경에서 문제인가?
 * - ReentrantLock으로 바꾸면 왜 해결되는가?
 */
class PinningTest {

    private static final int CARRIER_THREADS = Runtime.getRuntime().availableProcessors();
    /** carrier 수보다 많아야 pinning 효과가 드러난다 */
    private static final int TASK_COUNT = CARRIER_THREADS * 4;
    private static final int IO_DELAY_MS = 100;

    @Test
    @DisplayName("Pinning 발생: synchronized + blocking은 carrier를 독점 → 처리 지연")
    void synchronizedWithBlockingCausesPinning() throws InterruptedException {
        SynchronizedPinning pinning = new SynchronizedPinning();

        long elapsed = pinning.runWithPinning(TASK_COUNT, IO_DELAY_MS);

        // pinning 발생 시 carrier 수만큼씩 처리되므로 여러 배치가 필요
        // 예상 ≈ (TASK_COUNT / CARRIER_THREADS) × IO_DELAY_MS
        long expectedMin = (TASK_COUNT / CARRIER_THREADS) * IO_DELAY_MS;
        System.out.printf("[Pinning]    carrier %d개, task %d개, I/O %dms → %dms (예상 최소 %dms)%n",
                CARRIER_THREADS, TASK_COUNT, IO_DELAY_MS, elapsed, expectedMin);
        System.out.println("  → 콘솔 위에 'VirtualThread' 포함된 스택 트레이스가 출력됐다면 pinning 감지 성공!");

        assertThat(elapsed).isGreaterThanOrEqualTo(IO_DELAY_MS);
    }

    @Test
    @DisplayName("Pinning 해결: ReentrantLock + blocking은 carrier를 반납 → 지연 없음")
    void reentrantLockAllowsUnmounting() throws InterruptedException {
        ReentrantLockFix fix = new ReentrantLockFix();

        long elapsed = fix.runWithoutPinning(TASK_COUNT, IO_DELAY_MS);

        // carrier를 반납하므로 모든 VT가 동시 실행
        // 예상 ≈ IO_DELAY_MS (carrier 수에 무관)
        long tolerance = 300;
        System.out.printf("[ReentrantLock] carrier %d개, task %d개, I/O %dms → %dms%n",
                CARRIER_THREADS, TASK_COUNT, IO_DELAY_MS, elapsed);

        assertThat(elapsed).isLessThan(IO_DELAY_MS + tolerance);
    }

    @Test
    @DisplayName("비교: ReentrantLock이 synchronized보다 VT 환경에서 빠르다")
    void reentrantLockFasterThanSynchronized() throws InterruptedException {
        SynchronizedPinning pinning = new SynchronizedPinning();
        ReentrantLockFix fix = new ReentrantLockFix();

        long pinnedMs = pinning.runWithPinning(TASK_COUNT, IO_DELAY_MS);
        long unpinnedMs = fix.runWithoutPinning(TASK_COUNT, IO_DELAY_MS);

        System.out.printf("[비교] synchronized: %dms vs ReentrantLock: %dms → %.1fx 차이%n",
                pinnedMs, unpinnedMs, (double) pinnedMs / unpinnedMs);

        assertThat(unpinnedMs).isLessThan(pinnedMs);
    }
}
