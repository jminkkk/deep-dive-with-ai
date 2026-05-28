package com.jiminkkk.messagebroker.chapter3;

import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.model.RecordMetadata;
import com.jiminkkk.messagebroker.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Producer의 핵심 동작을 검증한다.
 *
 * <p>학습 포인트:
 * <ol>
 *   <li>send()는 RecordMetadata를 반환한다 (어디에 기록됐는지)</li>
 *   <li>키가 있으면 항상 같은 파티션으로 라우팅된다</li>
 *   <li>발행 후 파티션 로그에서 즉시 읽을 수 있다</li>
 * </ol>
 */
class ProducerTest {

    private Topic topic;
    private Producer producer;

    @BeforeEach
    void setUp() {
        topic = new Topic("user-events", 3);
        producer = new Producer(topic);
    }

    @Test
    @DisplayName("send()는 토픽명, 파티션, offset을 담은 RecordMetadata를 반환한다")
    void send_ReturnsRecordMetadata() {
        // When
        RecordMetadata meta = producer.send("user-1", "login");

        // Then
        assertThat(meta.topic()).isEqualTo("user-events");
        assertThat(meta.partition()).isBetween(0, 2);
        assertThat(meta.offset()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("같은 키로 발행한 레코드는 항상 같은 파티션에 순서대로 기록된다")
    void send_SameKeyPreservesOrder() {
        // Given: user-42의 이벤트 3개
        RecordMetadata m1 = producer.send("user-42", "signup");
        RecordMetadata m2 = producer.send("user-42", "login");
        RecordMetadata m3 = producer.send("user-42", "purchase");

        // Then: 같은 파티션, 증가하는 offset → 순서 보장
        assertThat(m1.partition()).isEqualTo(m2.partition()).isEqualTo(m3.partition());
        assertThat(m1.offset()).isLessThan(m2.offset());
        assertThat(m2.offset()).isLessThan(m3.offset());
    }

    @Test
    @DisplayName("발행된 레코드는 파티션에서 즉시 읽을 수 있다")
    void send_RecordIsImmediatelyReadable() {
        // When
        RecordMetadata meta = producer.send("key", "hello-kafka");

        // Then: 발행된 파티션의 해당 offset에서 읽기
        List<Record> records = topic.partition(meta.partition()).read(meta.offset(), 1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).value()).isEqualTo("hello-kafka");
        assertThat(records.get(0).key()).isEqualTo("key");
    }

    @Test
    @DisplayName("대량 발행 시 모든 레코드가 손실 없이 파티션에 기록된다")
    void send_BulkSendNoLoss() {
        // Given: 1000개 레코드 발행
        int count = 1000;
        for (int i = 0; i < count; i++) {
            producer.send("key-" + (i % 10), "value-" + i);
        }

        // Then: 모든 파티션의 레코드 합산이 1000
        long total = 0;
        for (int p = 0; p < topic.numPartitions(); p++) {
            total += topic.partition(p).size();
        }
        assertThat(total).isEqualTo(count);
    }

    @Test
    @DisplayName("키 없는 레코드는 여러 파티션에 고르게 분산된다")
    void send_NullKeyDistributesAcrossPartitions() {
        // Given: 파티션 3개 토픽에 키 없이 300개 발행
        Topic multiPartitionTopic = new Topic("no-key-topic", 3);
        Producer p = new Producer(multiPartitionTopic);

        for (int i = 0; i < 300; i++) {
            p.send(null, "value-" + i);
        }

        // Then: 각 파티션에 정확히 100개 (라운드 로빈)
        for (int part = 0; part < 3; part++) {
            assertThat(multiPartitionTopic.partition(part).size()).isEqualTo(100L);
        }
    }
}
