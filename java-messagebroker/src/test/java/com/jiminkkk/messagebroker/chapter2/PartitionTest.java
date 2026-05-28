package com.jiminkkk.messagebroker.chapter2;

import com.jiminkkk.messagebroker.core.HashPartitioner;
import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.model.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Topic과 Partition의 핵심 동작을 검증한다.
 *
 * <p>학습 포인트:
 * <ol>
 *   <li>같은 키는 항상 같은 파티션으로 라우팅된다</li>
 *   <li>파티션 내에서는 순서(offset)가 보장된다</li>
 *   <li>파티션이 병렬성의 단위다</li>
 * </ol>
 */
class PartitionTest {

    @Test
    @DisplayName("같은 키를 가진 레코드는 항상 같은 파티션으로 라우팅된다")
    void sameKey_AlwaysRoutesToSamePartition() {
        // Given: 파티션 4개짜리 토픽
        Topic topic = new Topic("orders", 4);

        // When: 같은 키로 여러 번 발행
        RecordMetadata m1 = topic.append(Record.of("user-123", "order-1"));
        RecordMetadata m2 = topic.append(Record.of("user-123", "order-2"));
        RecordMetadata m3 = topic.append(Record.of("user-123", "order-3"));

        // Then: 모두 같은 파티션 → user-123의 이벤트는 순서가 보장됨
        assertThat(m1.partition()).isEqualTo(m2.partition());
        assertThat(m2.partition()).isEqualTo(m3.partition());
    }

    @Test
    @DisplayName("다른 키는 다른 파티션으로 분산될 수 있다")
    void differentKeys_CanGoToDifferentPartitions() {
        // Given
        Topic topic = new Topic("events", 8);

        // When: 다양한 키로 레코드 발행
        Set<Integer> usedPartitions = new HashSet<>();
        String[] keys = {"user-1", "user-2", "user-3", "user-4", "user-5"};
        for (String key : keys) {
            RecordMetadata meta = topic.append(Record.of(key, "data"));
            usedPartitions.add(meta.partition());
        }

        // Then: 여러 파티션에 분산됨 (완벽한 균등 분산은 아닐 수 있음)
        assertThat(usedPartitions.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("키 없는 레코드는 라운드 로빈으로 파티션에 분산된다")
    void nullKey_RoundRobinDistribution() {
        // Given: 파티션 3개짜리 토픽
        Topic topic = new Topic("logs", 3);

        // When: 키 없이 3개 발행
        Set<Integer> partitions = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            RecordMetadata meta = topic.append(Record.of(null, "log-" + i));
            partitions.add(meta.partition());
        }

        // Then: 3개 파티션이 모두 사용됨 (라운드 로빈)
        assertThat(partitions).hasSize(3);
    }

    @Test
    @DisplayName("파티션 내에서는 offset이 순서대로 증가한다")
    void partition_OffsetIncreasesSequentially() {
        // Given
        Topic topic = new Topic("payments", 1); // 파티션 1개 → 모든 레코드가 같은 파티션

        // When
        RecordMetadata m1 = topic.append(Record.of("key", "payment-1"));
        RecordMetadata m2 = topic.append(Record.of("key", "payment-2"));
        RecordMetadata m3 = topic.append(Record.of("key", "payment-3"));

        // Then: 같은 파티션 내 offset은 순서대로 증가
        assertThat(m1.offset()).isLessThan(m2.offset());
        assertThat(m2.offset()).isLessThan(m3.offset());
    }

    @Test
    @DisplayName("Consumer 수보다 파티션이 많으면 일부 Consumer는 여러 파티션을 담당한다")
    void morePartitionsThanConsumers_SomeGetMultiple() {
        // Given: 파티션 6개, Consumer 4개
        // → Consumer 0,1이 각 2개 파티션 담당, Consumer 2,3이 각 1개 파티션 담당
        int numPartitions = 6;
        int numConsumers = 4;

        int[] assignment = new int[numConsumers];
        for (int p = 0; p < numPartitions; p++) {
            assignment[p % numConsumers]++;
        }

        // Then: Consumer당 파티션 수 = 1 또는 2 (균등하지 않을 수 있음)
        assertThat(assignment[0]).isEqualTo(2); // partition 0, 4
        assertThat(assignment[1]).isEqualTo(2); // partition 1, 5
        assertThat(assignment[2]).isEqualTo(1); // partition 2
        assertThat(assignment[3]).isEqualTo(1); // partition 3
    }
}
