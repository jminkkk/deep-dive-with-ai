package com.jiminkkk.messagebroker.core;

import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.model.RecordMetadata;

import java.util.List;

/**
 * 토픽을 구성하는 파티션 — 독립된 CommitLog 하나.
 *
 * <p>Kafka 토픽은 여러 파티션으로 분리된다.
 * 각 파티션은 독립된 순서를 가진 로그다.
 * 파티션이 병렬성과 확장성의 단위다.
 *
 * <p>핵심:
 * <ul>
 *   <li>파티션 내 순서는 보장된다 (offset 순)</li>
 *   <li>파티션 간 순서는 보장되지 않는다</li>
 *   <li>파티션이 많을수록 병렬 Consumer를 늘릴 수 있다</li>
 * </ul>
 *
 * @see <a href="https://kafka.apache.org/documentation/#intro_concepts_and_terms">Partitions</a>
 */
public class Partition {

    private final String topicName;
    private final int partitionId;
    private final CommitLog log = new CommitLog();

    public Partition(String topicName, int partitionId) {
        this.topicName = topicName;
        this.partitionId = partitionId;
    }

    /**
     * 레코드를 이 파티션 로그에 추가한다.
     *
     * @return 기록 위치를 담은 RecordMetadata
     */
    public RecordMetadata append(Record record) {
        long offset = log.append(record);
        return new RecordMetadata(topicName, partitionId, offset);
    }

    /**
     * 지정한 offset부터 레코드를 읽는다.
     */
    public List<Record> read(long fromOffset, int maxRecords) {
        return log.read(fromOffset, maxRecords);
    }

    public long size() {
        return log.size();
    }

    public long nextOffset() {
        return log.nextOffset();
    }

    public int partitionId() {
        return partitionId;
    }

    public String topicName() {
        return topicName;
    }
}
