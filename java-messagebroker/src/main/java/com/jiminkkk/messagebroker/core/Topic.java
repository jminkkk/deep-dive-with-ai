package com.jiminkkk.messagebroker.core;

import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.model.RecordMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kafka 토픽 — 파티션들의 논리적 묶음.
 *
 * <p>토픽은 파일 시스템의 폴더처럼, 이벤트(레코드)를 카테고리별로 분류하는 단위다.
 * 내부적으로는 numPartitions개의 {@link Partition}으로 구성된다.
 *
 * <p>Partitioner가 각 레코드가 어느 파티션으로 갈지 결정한다:
 * <ul>
 *   <li>키 있음 → hash(key) % numPartitions → 같은 키는 항상 같은 파티션</li>
 *   <li>키 없음 → 라운드 로빈</li>
 * </ul>
 *
 * @see <a href="https://kafka.apache.org/documentation/#intro_concepts_and_terms">Topics</a>
 */
public class Topic {

    private final String name;
    private final List<Partition> partitions;
    private final Partitioner partitioner;

    public Topic(String name, int numPartitions) {
        this(name, numPartitions, new HashPartitioner());
    }

    public Topic(String name, int numPartitions, Partitioner partitioner) {
        this.name = name;
        this.partitioner = partitioner;
        List<Partition> parts = new ArrayList<>(numPartitions);
        for (int i = 0; i < numPartitions; i++) {
            parts.add(new Partition(name, i));
        }
        this.partitions = Collections.unmodifiableList(parts);
    }

    /**
     * 레코드를 적절한 파티션에 추가한다.
     * 파티션 선택은 Partitioner가 담당한다.
     */
    public RecordMetadata append(Record record) {
        int partitionIdx = partitioner.partition(record.key(), partitions.size());
        return partitions.get(partitionIdx).append(record);
    }

    public Partition partition(int partitionId) {
        return partitions.get(partitionId);
    }

    public int numPartitions() {
        return partitions.size();
    }

    public String name() {
        return name;
    }

    public List<Partition> partitions() {
        return partitions;
    }
}
