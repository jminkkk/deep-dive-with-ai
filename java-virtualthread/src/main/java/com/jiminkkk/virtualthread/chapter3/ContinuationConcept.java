package com.jiminkkk.virtualthread.chapter3;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

/**
 * Virtual Thread의 핵심 메커니즘인 Continuation 개념을 직접 시연한다.
 *
 * <p>⚠️ 교육용 추상화: Java의 실제 Continuation은 {@code jdk.internal.vm.Continuation}으로
 * JVM 내부 API다. 이 클래스는 {@link LockSupport#park()}/{@link LockSupport#unpark(Thread)}를
 * 사용해 "suspend → 다른 작업 실행 → resume" 흐름을 직접 보여준다.
 *
 * <p>실제 Virtual Thread에서 continuation이 동작하는 방식:
 * <ol>
 *   <li>VT가 {@code Thread.sleep()}, socket read 등 blocking 호출을 만남</li>
 *   <li>JDK 내부에서 {@code VirtualThread#park()} 호출 → LockSupport.park()로 현재 VT 일시 중지</li>
 *   <li>Carrier thread가 다른 VT로 전환 (mount)</li>
 *   <li>I/O 완료 / sleep 만료 시 JDK 스케줄러가 {@code VirtualThread#unpark()} 호출</li>
 *   <li>Carrier thread가 VT를 다시 mount → 중단된 지점부터 재개</li>
 * </ol>
 *
 * <p>실제 코드 참조: openjdk {@code java.lang.VirtualThread#park()} 및
 * {@code java.lang.VirtualThread#parkNanos(long)}
 *
 * @see <a href="https://openjdk.org/jeps/444#Implementation">JEP 444: Implementation</a>
 */
public class ContinuationConcept {

    private volatile Thread suspendedThread;
    private volatile String resumeValue;
    /** park()가 호출되기 직전에 countdown — resume()이 안전하게 unpark()를 호출하도록 동기화 */
    private final CountDownLatch aboutToParkLatch = new CountDownLatch(1);

    /**
     * 작업을 시작하고 중간에 일시 중지(park)한다.
     * 실제 VT에서 blocking 호출이 내부적으로 하는 일을 직접 보여준다.
     *
     * @param initialValue park 이전에 처리된 값
     * @return initialValue + resumeValue (park 이후 재개된 후 합산)
     */
    public String runWithSuspend(String initialValue) {
        suspendedThread = Thread.currentThread();
        aboutToParkLatch.countDown();   // resume()에게 "이제 park 직전"을 알림

        LockSupport.park();             // ← 여기서 실행 중단 (carrier thread 반납)

        // --- 재개된 후 이 줄부터 실행된다 ---
        return initialValue + " + " + resumeValue;
    }

    /**
     * 일시 중지된 작업을 재개(unpark)한다.
     * 실제 VT에서는 I/O 완료 후 JDK 스케줄러가 자동으로 이 역할을 한다.
     *
     * @param value park 이후에 결합할 값
     */
    public void resume(String value) throws InterruptedException {
        aboutToParkLatch.await();       // park()가 호출될 때까지 대기
        this.resumeValue = value;
        LockSupport.unpark(suspendedThread);  // ← 중단된 VT를 스케줄러 큐에 다시 넣음
    }
}
