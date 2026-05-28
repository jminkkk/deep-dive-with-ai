package com.jiminkkk.messagebroker.consumer;

import com.jiminkkk.messagebroker.core.Partition;
import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka Consumer — 파티션에서 레코드를 읽는 클라이언트.
 *
 * <p>Consumer는 구독한 파티션의 현재 위치(offset)를 추적한다.
 * offset에는 두 종류가 있다:
 * <ul>
 *   <li><b>currentOffset</b>: 다음에 읽을 위치. poll() 후 즉시 증가.</li>
 *   <li><b>committedOffset</b>: "여기까지 처리 완료"를 브로커에 알린 위치.
 *       장애 복구 시 이 위치부터 다시 읽는다.</li>
 * </ul>
 *
 * <p>두 offset의 차이가 Delivery Semantics의 핵심이다.
 * commit 시점을 언제로 하느냐에 따라 at-least-once / at-most-once가 결정된다.
 *
 * @see <a href="https://kafka.apache.org/documentation/#theconsumer">The Consumer</a>
 */
public class Consumer {

    private final String consumerId;
    // 각 파티션의 현재 읽기 위치 (다음에 읽을 offset)
    private final Map<Integer, Long> currentOffsets = new HashMap<>();
    // 각 파티션의 커밋된 위치 (처리 완료 확인된 offset)
    private final Map<Integer, Long> committedOffsets = new HashMap<>();

    private List<Integer> assignedPartitions = Collections.emptyList();

    public Consumer(String consumerId) {
        this.consumerId = consumerId;
    }

    /**
     * 할당된 파티션에서 레코드를 읽는다.
     *
     * <p>실제 Kafka의 poll()과 동일한 역할.
     * 읽은 직후 currentOffset을 증가시키지만 committedOffset은 변경하지 않는다.
     *
     * @param topic      읽을 토픽
     * @param maxRecords 파티션당 최대 레코드 수
     * @return 읽은 레코드 목록
     */
    public List<Record> poll(Topic topic, int maxRecords) {
        List<Record> result = new ArrayList<>();
        for (int partitionId : assignedPartitions) {
            Partition partition = topic.partition(partitionId);
            long fromOffset = currentOffsets.getOrDefault(partitionId, 0L);
            List<Record> records = partition.read(fromOffset, maxRecords);
            result.addAll(records);
            if (!records.isEmpty()) {
                currentOffsets.put(partitionId, fromOffset + records.size());
            }
        }
        return result;
    }

    /**
     * 현재 읽은 위치를 커밋한다 — "여기까지 처리 완료"를 선언.
     *
     * <p>실제 Kafka에서는 이 정보가 브로커(__consumer_offsets 토픽)에 저장되어
     * Consumer 재시작 시 이 위치부터 다시 읽을 수 있게 된다.
     */
    public void commitSync() {
        committedOffsets.putAll(currentOffsets);
    }

    /**
     * 지정한 파티션의 읽기 위치를 강제로 변경한다.
     *
     * <p>실제 Kafka의 seek()과 동일. 로그를 처음부터 다시 읽거나(replay)
     * 특정 시점으로 되감기(rewind)할 때 사용한다.
     */
    public void seek(int partitionId, long offset) {
        currentOffsets.put(partitionId, offset);
    }

    /**
     * 장애 복구 시: 커밋된 위치로 되돌아간다.
     *
     * <p>처리 중 오류 발생 시 commitSync() 없이 이 메서드를 호출하면
     * 마지막으로 성공한 commit 위치부터 다시 처리할 수 있다.
     */
    public void seekToCommitted() {
        for (int partitionId : assignedPartitions) {
            long committed = committedOffsets.getOrDefault(partitionId, 0L);
            currentOffsets.put(partitionId, committed);
        }
    }

    /** 파티션 할당 (ConsumerGroup이 호출) */
    public void assign(List<Integer> partitionIds) {
        this.assignedPartitions = new ArrayList<>(partitionIds);
        for (int id : partitionIds) {
            currentOffsets.putIfAbsent(id, committedOffsets.getOrDefault(id, 0L));
        }
    }

    public long currentOffset(int partitionId) {
        return currentOffsets.getOrDefault(partitionId, 0L);
    }

    public long committedOffset(int partitionId) {
        return committedOffsets.getOrDefault(partitionId, 0L);
    }

    public List<Integer> assignedPartitions() {
        return Collections.unmodifiableList(assignedPartitions);
    }

    public String consumerId() {
        return consumerId;
    }
}
