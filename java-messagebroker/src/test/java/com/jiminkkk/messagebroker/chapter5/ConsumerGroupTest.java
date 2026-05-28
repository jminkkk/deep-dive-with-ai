package com.jiminkkk.messagebroker.chapter5;

import com.jiminkkk.messagebroker.consumer.Consumer;
import com.jiminkkk.messagebroker.consumer.ConsumerGroup;
import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsumerGroup의 핵심 동작을 검증한다.
 *
 * <p>학습 포인트:
 * <ol>
 *   <li>하나의 파티션은 그룹 내 하나의 Consumer에만 할당된다</li>
 *   <li>Consumer가 추가/제거되면 Rebalance로 파티션이 재분배된다</li>
 *   <li>Consumer 수 > 파티션 수이면 초과 Consumer는 놀게 된다</li>
 *   <li>그룹 전체가 협력하면 모든 레코드를 한 번씩 처리할 수 있다</li>
 * </ol>
 */
class ConsumerGroupTest {

    private Topic topic;
    private Producer producer;
    private ConsumerGroup group;

    @BeforeEach
    void setUp() {
        topic = new Topic("orders", 4); // 파티션 4개
        producer = new Producer(topic);
        group = new ConsumerGroup("order-processors");
    }

    @Test
    @DisplayName("Consumer가 join하면 파티션이 고르게 분배된다")
    void join_DistributesPartitionsEvenly() {
        // Given: Consumer 2개 참여
        Consumer c1 = new Consumer("c1");
        Consumer c2 = new Consumer("c2");

        // When
        group.join(c1, topic);
        group.join(c2, topic);

        // Then: 파티션 4개 → 각 Consumer에 2개씩
        assertThat(c1.assignedPartitions()).hasSize(2);
        assertThat(c2.assignedPartitions()).hasSize(2);
        // 중복 할당 없음
        assertThat(c1.assignedPartitions())
                .doesNotContainAnyElementsOf(c2.assignedPartitions());
    }

    @Test
    @DisplayName("Consumer가 leave하면 남은 Consumer들이 파티션을 재분배받는다 — Rebalance")
    void leave_TriggersRebalance() {
        // Given
        Consumer c1 = new Consumer("c1");
        Consumer c2 = new Consumer("c2");
        group.join(c1, topic);
        group.join(c2, topic);

        // When: c2가 나감
        group.leave(c2, topic);

        // Then: c1이 모든 파티션 담당
        assertThat(c1.assignedPartitions()).hasSize(4);
    }

    @Test
    @DisplayName("Consumer 수 > 파티션 수이면 초과 Consumer는 파티션을 받지 못한다")
    void moreConsumersThanPartitions_ExcessConsumersIdle() {
        // Given: 파티션 4개, Consumer 6개
        List<Consumer> consumers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Consumer c = new Consumer("c" + i);
            consumers.add(c);
            group.join(c, topic);
        }

        // Then: 4개는 파티션 1개씩, 2개는 파티션 없음
        long active = consumers.stream()
                .filter(c -> !c.assignedPartitions().isEmpty())
                .count();
        long idle = consumers.stream()
                .filter(c -> c.assignedPartitions().isEmpty())
                .count();

        assertThat(active).isEqualTo(4);
        assertThat(idle).isEqualTo(2);
    }

    @Test
    @DisplayName("그룹 내 모든 Consumer가 poll하면 중복 없이 모든 레코드를 처리한다")
    void group_ProcessesAllRecordsExactlyOnce() {
        // Given: 파티션 2개, Consumer 2개
        Topic t = new Topic("events", 2);
        Producer p = new Producer(t);
        ConsumerGroup g = new ConsumerGroup("workers");

        Consumer c1 = new Consumer("c1");
        Consumer c2 = new Consumer("c2");
        g.join(c1, t);
        g.join(c2, t);

        // 각 파티션에 키 고정으로 레코드 5개씩 발행
        for (int i = 0; i < 10; i++) {
            p.send("partition-" + (i % 2), "msg-" + i); // key에 따라 파티션 고정
        }

        // When: 각 Consumer가 자신의 파티션 poll
        List<Record> processed = new ArrayList<>();
        processed.addAll(c1.poll(t, 100));
        processed.addAll(c2.poll(t, 100));

        // Then: 총 10개, 중복 없음
        assertThat(processed).hasSize(10);
        long uniqueValues = processed.stream().map(Record::value).distinct().count();
        assertThat(uniqueValues).isEqualTo(10);
    }

    @Test
    @DisplayName("새 Consumer가 join하면 이전 Consumer의 할당이 재조정된다")
    void newConsumerJoins_ExistingAssignmentRebalanced() {
        // Given: Consumer 1개로 시작 → 파티션 4개 모두 담당
        Consumer c1 = new Consumer("c1");
        group.join(c1, topic);
        assertThat(c1.assignedPartitions()).hasSize(4);

        // When: Consumer 추가
        Consumer c2 = new Consumer("c2");
        group.join(c2, topic);

        // Then: 각 2개로 재분배
        assertThat(c1.assignedPartitions()).hasSize(2);
        assertThat(c2.assignedPartitions()).hasSize(2);
    }
}
