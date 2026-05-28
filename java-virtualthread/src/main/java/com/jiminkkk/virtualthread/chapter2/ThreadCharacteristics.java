package com.jiminkkk.virtualthread.chapter2;

/**
 * Virtual Thread와 플랫폼 스레드의 특성 차이를 비교한다.
 *
 * <p>Virtual Thread는 일부 특성이 고정(immutable)되어 있다:
 * <ul>
 *   <li>{@code daemon}: 항상 {@code true} — 변경 시 {@link IllegalArgumentException}</li>
 *   <li>{@code priority}: 항상 {@link Thread#NORM_PRIORITY}(5) — 변경해도 무시됨</li>
 *   <li>{@code ThreadGroup}: 고정된 "VirtualThreads" 그룹 소속</li>
 * </ul>
 *
 * <p>이 고정 특성들은 의도적인 설계다(JEP 444 Goals):
 * Virtual Thread는 경량 실행 단위로, 플랫폼 스레드처럼 세밀하게 제어할 필요가 없다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Goals">JEP 444: Goals</a>
 */
public class ThreadCharacteristics {

    /**
     * Virtual Thread와 플랫폼 스레드의 특성을 출력한다.
     */
    public static void printComparison(Thread platformThread, Thread virtualThread) {
        System.out.println("=== 스레드 특성 비교 ===");
        System.out.printf("%-20s %-20s %-20s%n", "특성", "플랫폼 스레드", "Virtual Thread");
        System.out.printf("%-20s %-20b %-20b%n", "isVirtual()", platformThread.isVirtual(), virtualThread.isVirtual());
        System.out.printf("%-20s %-20b %-20b%n", "isDaemon()", platformThread.isDaemon(), virtualThread.isDaemon());
        System.out.printf("%-20s %-20d %-20d%n", "getPriority()", platformThread.getPriority(), virtualThread.getPriority());
        System.out.printf("%-20s %-20s %-20s%n", "ThreadGroup",
                platformThread.getThreadGroup() != null ? platformThread.getThreadGroup().getName() : "null",
                virtualThread.getThreadGroup() != null ? virtualThread.getThreadGroup().getName() : "null");
    }
}
