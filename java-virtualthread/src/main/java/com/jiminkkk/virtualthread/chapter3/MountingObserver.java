package com.jiminkkk.virtualthread.chapter3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Virtual Thread의 mounting/unmounting 동작을 간접적으로 관찰한다.
 *
 * <p>Virtual Thread의 생명주기:
 * <pre>
 * CREATED → STARTED → RUNNING (carrier에 mount됨)
 *                    ↓ blocking (Thread.sleep, I/O 등)
 *                   PARKED (carrier에서 unmount — carrier는 다른 VT에 재사용)
 *                    ↓ unpark (I/O 완료, sleep 만료)
 *                   RUNNING (다시 mount — 이전과 다른 carrier일 수 있음)
 *                    ↓ 완료
 *                  TERMINATED
 * </pre>
 *
 * <p>⚠️ 직접 관찰의 한계: Java 21에서 VT가 실행 중인 carrier thread를 공개 API로
 * 직접 조회할 수 없다. {@code Thread.currentThread()}는 carrier가 아닌 VT 자신을 반환한다.
 * Carrier thread 정보는 JFR(Java Flight Recorder) 또는 스레드 덤프에서만 볼 수 있다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Scheduling">JEP 444: Scheduling</a>
 */
public class MountingObserver {

    private final List<String> log = new CopyOnWriteArrayList<>();

    /**
     * Virtual Thread 내에서 blocking 전후 상태를 기록한다.
     *
     * <p>blocking 전후에 같은 VT 객체({@code Thread.currentThread()})이지만,
     * 내부적으로는 다른 carrier thread 위에서 재개될 수 있다.
     */
    public void observeAroundBlocking(int taskId, int sleepMs) throws InterruptedException {
        Thread vt = Thread.currentThread();
        log.add(String.format("[task-%03d] BEFORE sleep | VT=%s | isVirtual=%b",
                taskId, vt.getName(), vt.isVirtual()));

        Thread.sleep(sleepMs);
        // 이 시점에서 같은 VT 객체이지만, 다른 carrier thread가 실행 중일 수 있다.
        // (JFR로 확인 가능 — 이 교육용 코드에서는 직접 관찰 불가)

        Thread vtAfter = Thread.currentThread();
        log.add(String.format("[task-%03d] AFTER  sleep | VT=%s | sameRef=%b",
                taskId, vtAfter.getName(), vt == vtAfter));
    }

    public List<String> getLog() {
        return List.copyOf(log);
    }

    /**
     * 기본 carrier thread 수를 반환한다.
     *
     * <p>⚠️ 교육용 추상화: 실제 carrier thread pool 크기는 JVM 내부 상태로
     * 공개 API로 정확히 조회할 수 없다. 기본값은 {@code availableProcessors()}이지만
     * {@code -Djdk.virtualThreadScheduler.parallelism=N} 으로 변경 가능하다.
     * 실제 값은 openjdk: {@code java.lang.VirtualThread#DEFAULT_SCHEDULER} 를 확인하라.
     */
    public int getDefaultCarrierThreadCount() {
        return Runtime.getRuntime().availableProcessors();
    }
}
