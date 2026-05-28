package com.jiminkkk.messagebroker.model;

/**
 * Producer가 레코드를 전송한 뒤 받는 응답 메타데이터.
 *
 * <p>어느 토픽의 어느 파티션에 몇 번 offset으로 기록됐는지를 담는다.
 * 실제 Kafka의 {@code RecordMetadata}와 동일한 역할이다.
 *
 * @see <a href="https://kafka.apache.org/36/javadoc/org/apache/kafka/clients/producer/RecordMetadata.html">RecordMetadata Javadoc</a>
 */
public record RecordMetadata(
        String topic,
        int partition,
        long offset
) {}
