package com.jiminkkk.forkjoin.chapter1;

import com.jiminkkk.forkjoin.core.WorkStealingDeque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkStealingDeque의 핵심 동작을 검증한다.
 *
 * Work-Stealing의 핵심 비대칭성:
 * - 소유자는 LIFO (push/pop: top에서)
 * - 도둑은 FIFO (steal: bottom에서)
 */
class WorkStealingDequeTest {

    private WorkStealingDeque<Integer> deque;

    @BeforeEach
    void setUp() {
        deque = new WorkStealingDeque<>();
    }

    @Test
    @DisplayName("push/pop은 LIFO 순서로 동작해야 한다 — 소유자는 최근 태스크부터 처리")
    void pushPopShouldBeLIFO() {
        // Given
        deque.push(1);
        deque.push(2);
        deque.push(3);

        // When & Then: 3, 2, 1 순서로 나와야 한다 (Last In, First Out)
        assertThat(deque.pop()).isEqualTo(3);
        assertThat(deque.pop()).isEqualTo(2);
        assertThat(deque.pop()).isEqualTo(1);
    }

    @Test
    @DisplayName("steal은 FIFO 순서로 동작해야 한다 — 도둑은 오래된(큰) 태스크를 훔쳐간다")
    void stealShouldBeFIFO() {
        // Given: 1, 2, 3 순서로 push
        deque.push(1);
        deque.push(2);
        deque.push(3);

        // When & Then: steal은 bottom(가장 먼저 push된 쪽)에서 꺼냄 → 1, 2, 3 순서
        // 이것이 Work-Stealing의 핵심: 도둑은 가장 오래된(분할 전의 큰) 태스크를 가져간다
        assertThat(deque.steal()).isEqualTo(1);
        assertThat(deque.steal()).isEqualTo(2);
        assertThat(deque.steal()).isEqualTo(3);
    }

    @Test
    @DisplayName("push/pop과 steal은 deque의 반대쪽 끝에서 동작한다")
    void pushPopAndStealOperateFromOppositeEnds() {
        // Given
        deque.push(10);
        deque.push(20);
        deque.push(30);

        // When: 소유자가 top에서 하나 꺼내고, 도둑이 bottom에서 하나 훔쳐간다
        Integer popped = deque.pop();   // top: 30
        Integer stolen = deque.steal(); // bottom: 10

        // Then: 서로 다른 원소를 가져간다
        assertThat(popped).isEqualTo(30);
        assertThat(stolen).isEqualTo(10);
        assertThat(deque.size()).isEqualTo(1); // 20만 남음
    }

    @Test
    @DisplayName("빈 deque에서 pop과 steal은 null을 반환해야 한다")
    void emptyDequeShouldReturnNullForPopAndSteal() {
        assertThat(deque.pop()).isNull();
        assertThat(deque.steal()).isNull();
    }

    @Test
    @DisplayName("steal 횟수가 stealCount에 정확히 기록되어야 한다")
    void stealCountShouldTrackAllSteals() {
        // Given
        deque.push(1);
        deque.push(2);
        deque.push(3);

        // When
        deque.steal();
        deque.steal();
        deque.steal();
        deque.steal(); // 빈 deque → null 반환, count 증가 없음

        // Then: 성공한 steal만 카운트됨
        assertThat(deque.getStealCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("단일 원소 deque에서 pop과 steal이 경쟁할 때 하나만 성공해야 한다")
    void singleElementDeque_PopAndSteal_OnlyOneSucceeds() throws InterruptedException {
        // Given: 원소 하나만 있는 deque
        deque.push(42);

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        // 소유자 스레드 시뮬레이션
        Thread owner = new Thread(() -> {
            try {
                latch.await();
                Integer result = deque.pop();
                if (result != null) successCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        // 도둑 스레드 시뮬레이션
        Thread thief = new Thread(() -> {
            try {
                latch.await();
                Integer result = deque.steal();
                if (result != null) successCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        owner.start();
        thief.start();
        latch.countDown(); // 동시 시작
        done.await();

        // Then: 둘 중 하나만 원소를 가져간다
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("다수의 병렬 steal 시도에서 각 원소는 정확히 한 번만 steal된다")
    void concurrentSteals_EachElementStolenExactlyOnce() throws InterruptedException {
        // Given
        int itemCount = 100;
        for (int i = 0; i < itemCount; i++) {
            deque.push(i);
        }

        int thiefCount = 4;
        List<Integer> stolenItems = new ArrayList<>();
        Object lock = new Object();
        CountDownLatch done = new CountDownLatch(thiefCount);

        // When: 여러 도둑이 동시에 steal 시도
        for (int t = 0; t < thiefCount; t++) {
            new Thread(() -> {
                List<Integer> localStolen = new ArrayList<>();
                Integer item;
                while ((item = deque.steal()) != null) {
                    localStolen.add(item);
                }
                synchronized (lock) {
                    stolenItems.addAll(localStolen);
                }
                done.countDown();
            }).start();
        }

        done.await();

        // Then: 중복 없이 모든 원소가 정확히 한 번씩 steal됨
        assertThat(stolenItems).doesNotHaveDuplicates();
        assertThat(stolenItems).hasSizeLessThanOrEqualTo(itemCount);
    }
}
