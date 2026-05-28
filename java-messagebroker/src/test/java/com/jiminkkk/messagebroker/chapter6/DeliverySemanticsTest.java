package com.jiminkkk.messagebroker.chapter6;

import com.jiminkkk.messagebroker.consumer.Consumer;
import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.producer.Producer;
import com.jiminkkk.messagebroker.model.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Delivery Semantics를 코드로 직접 확인한다.
 *
 * <p>학습 순서:
 * <ol>
 *   <li>[At-Most-Once]  commit → 처리 → 실패 시 유실 확인</li>
 *   <li>[At-Least-Once] 처리 → commit → 실패 시 재처리 확인</li>
 *   <li>[Rollback]      처리 실패 → seekToCommitted → 재처리 확인</li>
 * </ol>
 */
class DeliverySemanticsTest {

    private Topic topic;
    private Producer producer;
    private Consumer consumer;

    @BeforeEach
    void setUp() {
        topic = new Topic("payments", 1);
        producer = new Producer(topic);
        consumer = new Consumer("consumer-1");
        consumer.assign(List.of(0));

        // 5개 레코드 미리 발행
        for (int i = 1; i <= 5; i++) {
            producer.send("k", "payment-" + i);
        }
    }

    // ─── At-Most-Once ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[At-Most-Once] commit 후 처리 실패 시 레코드가 유실된다")
    void atMostOnce_LosesRecordOnFailure() {
        // Given: 처리 중 예외 발생 시뮬레이션
        AtomicInteger processed = new AtomicInteger(0);

        // When: commit 먼저, 처리 중 3번째 레코드에서 예외
        try {
            DeliverySemantics.atMostOnce(consumer, topic, record -> {
                if (record.value().equals("payment-3")) {
                    throw new RuntimeException("processing failed");
                }
                processed.incrementAndGet();
            });
        } catch (RuntimeException ignored) {}

        // Then: commit은 이미 완료됨 → 다음 poll은 6번째부터 시작 (1~5 유실)
        assertThat(consumer.committedOffset(0)).isEqualTo(5L);
        List<Record> nextBatch = consumer.poll(topic, 10);
        assertThat(nextBatch).isEmpty(); // 더 읽을 레코드 없음 → 유실 확인
    }

    // ─── At-Least-Once ───────────────────────────────────────────────────────

    @Test
    @DisplayName("[At-Least-Once] 정상 처리 후 commit — 중복 없이 1회 처리")
    void atLeastOnce_ProcessesOnceOnSuccess() {
        // Given
        List<String> processed = new ArrayList<>();

        // When
        DeliverySemantics.atLeastOnce(consumer, topic, record -> processed.add(record.value()));

        // Then: 5개 처리, commit 완료
        assertThat(processed).hasSize(5);
        assertThat(consumer.committedOffset(0)).isEqualTo(5L);
    }

    @Test
    @DisplayName("[At-Least-Once] 처리 실패 후 재시작 시 같은 레코드를 다시 처리한다 — 중복 가능")
    void atLeastOnce_ReprocessesOnFailure() {
        // Given: 3번째 레코드에서 처리 실패
        AtomicInteger processed = new AtomicInteger(0);
        try {
            DeliverySemantics.atLeastOnce(consumer, topic, record -> {
                if (record.value().equals("payment-3")) {
                    throw new RuntimeException("DB timeout");
                }
                processed.incrementAndGet();
            });
        } catch (RuntimeException ignored) {}

        // Then: 예외 발생 → commit 안 됨 → committedOffset은 여전히 0
        assertThat(consumer.committedOffset(0)).isEqualTo(0L);

        // When: 재시작 (seekToCommitted로 0으로 되돌아감)
        consumer.seekToCommitted();
        List<Record> retried = consumer.poll(topic, 10);

        // Then: 처음부터 다시 읽힘 → payment-1,2가 중복 처리될 수 있음
        assertThat(retried).hasSize(5);
        assertThat(retried.get(0).value()).isEqualTo("payment-1");
    }

    // ─── Rollback ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Rollback] 처리 실패 시 seekToCommitted로 되돌아가 재처리할 수 있다")
    void rollback_SeeksToCommittedOnFailure() {
        // Given: 처리 중 예외 발생 → commit 안 됨
        try {
            DeliverySemantics.atLeastOnce(consumer, topic, record -> {
                if (record.offset() >= 2) throw new RuntimeException("stop after 2");
            });
        } catch (RuntimeException ignored) {}
        // offset 0,1 처리 후 예외 → commit 안 됨
        // 실제로는 예외 발생 전 처리된 것도 commit 안 됨
        // seekToCommitted 후 offset 0부터 재처리
        consumer.seekToCommitted(); // committedOffset = 0으로 되돌아감

        List<Record> reprocessed = new ArrayList<>();
        DeliverySemantics.atLeastOnceWithRollback(consumer, topic,
                record -> reprocessed.add(record));

        // Then: 처음부터 다시 5개 처리
        assertThat(reprocessed).hasSize(5);
        assertThat(consumer.committedOffset(0)).isEqualTo(5L);
    }

    @Test
    @DisplayName("[Rollback] 처리 중 예외 발생 시 committedOffset이 변경되지 않는다")
    void rollback_DoesNotCommitOnException() {
        // Given: 처음 2개 정상 commit
        consumer.poll(topic, 2);
        consumer.commitSync();
        assertThat(consumer.committedOffset(0)).isEqualTo(2L);

        // When: 다음 처리 중 예외
        assertThatThrownBy(() ->
                DeliverySemantics.atLeastOnceWithRollback(consumer, topic, record -> {
                    throw new RuntimeException("fail");
                })
        ).isInstanceOf(RuntimeException.class);

        // Then: committedOffset은 2 그대로 (롤백됨)
        assertThat(consumer.committedOffset(0)).isEqualTo(2L);
        // currentOffset도 2로 되돌아옴
        assertThat(consumer.currentOffset(0)).isEqualTo(2L);
    }
}
