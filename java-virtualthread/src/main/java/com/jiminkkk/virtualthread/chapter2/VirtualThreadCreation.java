package com.jiminkkk.virtualthread.chapter2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Virtual Thread 생성 방법 5가지.
 *
 * <p>JEP 444에서 정의한 Virtual Thread API는 {@link Thread} 클래스에 통합되었다.
 * 플랫폼 스레드 API({@code Thread.ofPlatform()})와 대칭 구조를 가지므로
 * 기존 코드를 최소한으로 변경해 Virtual Thread로 전환할 수 있다.
 *
 * @see <a href="https://openjdk.org/jeps/444#Virtual-threads-in-the-Java-Platform">JEP 444: Virtual threads in the Java Platform</a>
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html">Thread Javadoc (Java SE 21)</a>
 */
public class VirtualThreadCreation {

    /**
     * 방법 1: {@code Thread.ofVirtual().start()} — 이름 지정 가능, 가장 권장.
     * 이름을 붙이면 디버깅 시 스레드 덤프에서 식별하기 쉽다.
     */
    public Thread createWithBuilder(String name, Runnable task) {
        return Thread.ofVirtual()
                .name(name)
                .start(task);
    }

    /**
     * 방법 2: {@code Thread.startVirtualThread()} — 이름 없이 간편하게 생성.
     * 빠른 프로토타이핑이나 이름이 필요 없는 단발성 작업에 적합.
     */
    public Thread createConvenience(Runnable task) {
        return Thread.startVirtualThread(task);
    }

    /**
     * 방법 3: {@code Thread.ofVirtual().unstarted()} — 생성만 하고 나중에 시작.
     * {@code thread.start()}를 직접 호출해야 실행된다.
     */
    public Thread createUnstarted(Runnable task) {
        return Thread.ofVirtual().unstarted(task);
    }

    /**
     * 방법 4: {@code ThreadFactory} — DI나 ExecutorService에 주입할 때 유용.
     * {@code name("prefix-", startIndex)}로 worker-0, worker-1... 자동 증가.
     */
    public ThreadFactory createFactory(String prefix) {
        return Thread.ofVirtual()
                .name(prefix, 0)
                .factory();
    }

    /**
     * 방법 5: {@code Executors.newVirtualThreadPerTaskExecutor()} — 작업마다 새 VT 생성.
     * I/O 바운드 고동시성 서버에서 가장 많이 쓰이는 패턴.
     * try-with-resources로 사용하면 {@code shutdown()} + {@code awaitTermination()}을 자동 처리.
     */
    public ExecutorService createExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
