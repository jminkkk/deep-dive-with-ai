package com.jiminkkk.virtualthread.chapter2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualThreadBasicsTest {

    @Test
    @DisplayName("isVirtual: Virtual Thread는 isVirtual() == true")
    void virtualThreadIsDetectable() throws InterruptedException {
        AtomicReference<Boolean> isVirtual = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread vt = Thread.ofVirtual().start(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });

        latch.await();
        assertThat(vt.isVirtual()).isTrue();
        assertThat(isVirtual.get()).isTrue();
    }

    @Test
    @DisplayName("daemon: Virtual Thread는 항상 daemon=true (변경 불가)")
    void virtualThreadIsAlwaysDaemon() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Thread vt = Thread.ofVirtual().start(latch::countDown);
        latch.await();

        assertThat(vt.isDaemon()).isTrue();
    }

    @Test
    @DisplayName("daemon 변경 불가: 생성 후 setDaemon(false) 시도는 IllegalArgumentException")
    void settingDaemonFalseOnVirtualThreadThrows() {
        // JEP 444: Virtual Thread의 daemon은 항상 true — Thread.Builder.OfVirtual에는 daemon() 메서드가 없다.
        // 이미 생성된 VT 객체에 setDaemon(false)를 시도하면 예외 발생.
        Thread vt = Thread.ofVirtual().unstarted(() -> {});
        assertThatThrownBy(() -> vt.setDaemon(false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("priority: Virtual Thread는 항상 NORM_PRIORITY(5)")
    void virtualThreadPriorityIsAlwaysNorm() throws InterruptedException {
        AtomicInteger priority = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> {
            priority.set(Thread.currentThread().getPriority());
            latch.countDown();
        });

        latch.await();
        assertThat(priority.get()).isEqualTo(Thread.NORM_PRIORITY);
    }

    @Test
    @DisplayName("이름 설정: Thread.ofVirtual().name()으로 이름 지정")
    void virtualThreadCanBeNamed() throws InterruptedException {
        AtomicReference<String> name = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual()
                .name("my-worker")
                .start(() -> {
                    name.set(Thread.currentThread().getName());
                    latch.countDown();
                });

        latch.await();
        assertThat(name.get()).isEqualTo("my-worker");
    }

    @Test
    @DisplayName("자동 이름: name(prefix, start)으로 순번 자동 증가")
    void virtualThreadNameAutoIncrement() throws Exception {
        var factory = Thread.ofVirtual().name("worker-",  0).factory();
        CountDownLatch latch = new CountDownLatch(3);
        AtomicReference<String>[] names = new AtomicReference[3];
        for (int i = 0; i < 3; i++) {
            names[i] = new AtomicReference<>();
            final int idx = i;
            factory.newThread(() -> {
                names[idx].set(Thread.currentThread().getName());
                latch.countDown();
            }).start();
        }
        latch.await();

        assertThat(names).extracting(AtomicReference::get)
                .containsExactlyInAnyOrder("worker-0", "worker-1", "worker-2");
    }

    @Test
    @DisplayName("unstarted: 생성 후 start()를 직접 호출해야 실행된다")
    void unstartedThreadRequiresManualStart() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Thread vt = Thread.ofVirtual().unstarted(latch::countDown);

        assertThat(vt.getState()).isEqualTo(Thread.State.NEW);  // 아직 실행 안 됨
        vt.start();
        latch.await();
        assertThat(vt.isVirtual()).isTrue();
    }

    @Test
    @DisplayName("특성 비교 출력: 플랫폼 vs Virtual Thread")
    void printThreadCharacteristics() throws InterruptedException {
        CountDownLatch platformLatch = new CountDownLatch(1);
        CountDownLatch virtualLatch = new CountDownLatch(1);

        Thread platformThread = Thread.ofPlatform().start(platformLatch::countDown);
        Thread virtualThread = Thread.ofVirtual().start(virtualLatch::countDown);

        platformLatch.await();
        virtualLatch.await();

        ThreadCharacteristics.printComparison(platformThread, virtualThread);

        assertThat(platformThread.isVirtual()).isFalse();
        assertThat(virtualThread.isVirtual()).isTrue();
    }
}
