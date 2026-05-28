package com.jiminkkk.messagebroker.chapter4;

import com.jiminkkk.messagebroker.consumer.Consumer;
import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.model.RecordMetadata;
import com.jiminkkk.messagebroker.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer의 핵심 동작을 검증한다.
 *
 * <p>학습 포인트:
 * <ol>
 *   <li>poll()은 currentOffset에서 읽고 위치를 전진시킨다</li>
 *   <li>commitSync()는 committedOffset을 현재 위치로 갱신한다</li>
 *   <li>seek()로 임의 위치로 이동하여 replay할 수 있다</li>
 *   <li>seekToCommitted()로 마지막 commit 위치로 되돌아갈 수 있다</li>
 * </ol>
 */
class ConsumerTest {

    private Topic topic;
    private Producer producer;
    private Consumer consumer;

    @BeforeEach
    void setUp() {
        topic = new Topic("events", 1); // 파티션 1개로 단순화
        producer = new Producer(topic);
        consumer = new Consumer("consumer-1");
        consumer.assign(List.of(0)); // 파티션 0 할당
    }

    @Test
    @DisplayName("poll()은 레코드를 읽고 currentOffset을 전진시킨다")
    void poll_AdvancesCurrentOffset() {
        // Given
        producer.send("k", "msg-1");
        producer.send("k", "msg-2");
        producer.send("k", "msg-3");

        // When
        List<Record> records = consumer.poll(topic, 2);

        // Then: 2개 읽고 offset은 2가 됨
        assertThat(records).hasSize(2);
        assertThat(consumer.currentOffset(0)).isEqualTo(2L);
    }

    @Test
    @DisplayName("commitSync()는 committedOffset을 currentOffset으로 갱신한다")
    void commitSync_UpdatesCommittedOffset() {
        // Given
        producer.send("k", "a");
        producer.send("k", "b");

        // When
        consumer.poll(topic, 2);
        assertThat(consumer.committedOffset(0)).isZero(); // 아직 commit 안 함

        consumer.commitSync();
        assertThat(consumer.committedOffset(0)).isEqualTo(2L); // commit 후 갱신
    }

    @Test
    @DisplayName("seek()으로 이미 읽은 offset으로 돌아가면 레코드를 다시 읽을 수 있다 — Replay")
    void seek_EnablesReplay() {
        // Given
        producer.send("k", "first");
        producer.send("k", "second");
        consumer.poll(topic, 10); // 모두 읽음
        consumer.commitSync();

        // When: offset 0으로 되감기
        consumer.seek(0, 0L);
        List<Record> replayed = consumer.poll(topic, 10);

        // Then: 처음부터 다시 읽힘
        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0).value()).isEqualTo("first");
    }

    @Test
    @DisplayName("seekToCommitted()는 committedOffset으로 currentOffset을 되돌린다")
    void seekToCommitted_RollsBackToCommitted() {
        // Given: 2개 읽고 1개만 commit
        producer.send("k", "a");
        producer.send("k", "b");
        producer.send("k", "c");

        consumer.poll(topic, 1); // "a" 읽음
        consumer.commitSync();   // offset 1 commit

        consumer.poll(topic, 1); // "b" 읽음 (commit 안 함)

        assertThat(consumer.currentOffset(0)).isEqualTo(2L);
        assertThat(consumer.committedOffset(0)).isEqualTo(1L);

        // When: commit 위치로 되돌아감
        consumer.seekToCommitted();

        // Then: "b"부터 다시 읽힘
        List<Record> records = consumer.poll(topic, 10);
        assertThat(records.get(0).value()).isEqualTo("b");
    }

    @Test
    @DisplayName("poll()을 반복하면 중복 없이 모든 레코드를 순서대로 읽는다")
    void poll_ConsumesAllRecordsInOrder() {
        // Given
        int count = 10;
        for (int i = 0; i < count; i++) {
            producer.send("k", "msg-" + i);
        }

        // When: 3개씩 나눠 읽기
        List<Record> all = new java.util.ArrayList<>();
        List<Record> batch;
        while (!(batch = consumer.poll(topic, 3)).isEmpty()) {
            all.addAll(batch);
        }

        // Then: 순서대로 10개
        assertThat(all).hasSize(count);
        for (int i = 0; i < count; i++) {
            assertThat(all.get(i).value()).isEqualTo("msg-" + i);
        }
    }
}
