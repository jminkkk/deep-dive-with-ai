package com.jiminkkk.messagebroker.core;

/**
 * 레코드를 어느 파티션에 쓸지 결정하는 전략 인터페이스.
 *
 * <p>실제 Kafka의 {@code org.apache.kafka.clients.producer.Partitioner}와
 * 동일한 역할이다.
 *
 * @see <a href="https://kafka.apache.org/documentation/#producerconfigs_partitioner.class">partitioner.class config</a>
 */
public interface Partitioner {

    /**
     * @param key           레코드 키 (null 가능)
     * @param numPartitions 토픽의 파티션 수
     * @return 선택된 파티션 인덱스 (0 이상 numPartitions 미만)
     */
    int partition(String key, int numPartitions);
}
