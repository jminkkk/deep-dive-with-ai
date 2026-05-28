package com.jiminkkk.messagebroker.consumer;

import com.jiminkkk.messagebroker.core.Topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka Consumer Group — 여러 Consumer가 협력하여 토픽을 분담 처리.
 *
 * <p>Consumer Group의 핵심 규칙:
 * <ul>
 *   <li>하나의 파티션은 그룹 내 단 하나의 Consumer에만 할당된다</li>
 *   <li>Consumer 수 > 파티션 수이면 일부 Consumer는 놀게 된다</li>
 *   <li>Consumer가 추가/제거되면 Rebalance가 발생하여 파티션을 재분배한다</li>
 * </ul>
 *
 * <p>교육용 단순화: 실제 Kafka의 Rebalance는 Group Coordinator(Broker)가
 * 관리하며 RangeAssignor / RoundRobinAssignor / StickyAssignor 전략이 있다.
 * 여기서는 라운드 로빈 방식만 구현한다.
 *
 * @see <a href="https://kafka.apache.org/documentation/#intro_consumers">Consumer Groups</a>
 */
public class ConsumerGroup {

    private final String groupId;
    private final List<Consumer> consumers = new ArrayList<>();
    // Consumer → 담당 파티션 목록
    private final Map<Consumer, List<Integer>> assignment = new LinkedHashMap<>();

    public ConsumerGroup(String groupId) {
        this.groupId = groupId;
    }

    /**
     * 그룹에 Consumer를 추가하고 rebalance를 수행한다.
     */
    public void join(Consumer consumer, Topic topic) {
        consumers.add(consumer);
        rebalance(topic);
    }

    /**
     * 그룹에서 Consumer를 제거하고 rebalance를 수행한다.
     */
    public void leave(Consumer consumer, Topic topic) {
        consumers.remove(consumer);
        assignment.remove(consumer);
        rebalance(topic);
    }

    /**
     * 파티션을 Consumer들에게 라운드 로빈으로 재분배한다.
     *
     * <p>실제 Kafka의 Rebalance 프로토콜:
     * 1. Consumer가 Group Coordinator에 JoinGroup 요청
     * 2. Coordinator가 리더 Consumer를 선출
     * 3. 리더가 파티션 할당 계획을 SyncGroup에 제출
     * 4. 모든 Consumer가 새 할당을 적용
     *
     * <p>여기서는 이 과정을 단순화하여 라운드 로빈으로 직접 할당한다.
     */
    public void rebalance(Topic topic) {
        assignment.clear();
        for (Consumer consumer : consumers) {
            assignment.put(consumer, new ArrayList<>());
        }

        if (consumers.isEmpty()) return;

        // 라운드 로빈: 파티션 0, 1, 2... 를 Consumer 0, 1, 2... 에 순서대로
        int numPartitions = topic.numPartitions();
        for (int partitionId = 0; partitionId < numPartitions; partitionId++) {
            Consumer assignee = consumers.get(partitionId % consumers.size());
            assignment.get(assignee).add(partitionId);
        }

        // 각 Consumer에 할당 결과 통보
        for (Map.Entry<Consumer, List<Integer>> entry : assignment.entrySet()) {
            entry.getKey().assign(entry.getValue());
        }
    }

    public Map<Consumer, List<Integer>> assignment() {
        return Collections.unmodifiableMap(assignment);
    }

    public List<Consumer> consumers() {
        return Collections.unmodifiableList(consumers);
    }

    public String groupId() {
        return groupId;
    }
}
