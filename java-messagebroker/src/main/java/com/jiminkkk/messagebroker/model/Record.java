package com.jiminkkk.messagebroker.model;

/**
 * Kafka의 기본 데이터 단위 — 레코드(메시지).
 *
 * <p>실제 Kafka 레코드는 key, value, timestamp, headers, offset으로 구성된다.
 * offset은 레코드가 파티션 로그에 기록될 때 Broker가 부여한다.
 *
 * <p>교육용 단순화: 실제 Kafka는 바이트 직렬화(byte[])를 사용하지만
 * 여기서는 String으로 표현하여 가독성을 높였다.
 *
 * @see <a href="https://kafka.apache.org/documentation/#intro_concepts_and_terms">Kafka Concepts</a>
 */
public record Record(
        long offset,
        String key,
        String value,
        long timestamp
) {
    /** offset이 아직 결정되지 않은 ProducerRecord 생성용 팩토리 */
    public static Record of(String key, String value) {
        return new Record(-1L, key, value, System.currentTimeMillis());
    }

    /** offset을 부여하여 새 Record 반환 (CommitLog가 사용) */
    public Record withOffset(long offset) {
        return new Record(offset, this.key, this.value, this.timestamp);
    }
}
