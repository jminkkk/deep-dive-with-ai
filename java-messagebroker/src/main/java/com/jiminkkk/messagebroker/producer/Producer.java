package com.jiminkkk.messagebroker.producer;

import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;
import com.jiminkkk.messagebroker.model.RecordMetadata;

/**
 * Kafka Producer — 토픽에 레코드를 발행하는 클라이언트.
 *
 * <p>실제 Kafka Producer는 네트워크를 통해 Broker에 레코드를 전송한다.
 * 이 교육용 구현에서는 네트워크 없이 토픽에 직접 접근한다.
 *
 * <p>실제 Kafka Producer의 핵심 기능들 (교육용 단순화):
 * <ul>
 *   <li><b>Batching</b>: 여러 레코드를 묶어 한 번에 전송 → 처리량 향상 (단순화: 1건씩 즉시 전송)</li>
 *   <li><b>Compression</b>: 배치를 압축하여 네트워크 비용 절감 (생략)</li>
 *   <li><b>Acks</b>: 리더만 확인(acks=1) vs 전체 ISR 확인(acks=all) (생략)</li>
 *   <li><b>Idempotent</b>: 재시도 시 중복 방지 (생략)</li>
 * </ul>
 *
 * @see <a href="https://kafka.apache.org/documentation/#theproducer">The Producer</a>
 */
public class Producer {

    private final Topic topic;

    public Producer(Topic topic) {
        this.topic = topic;
    }

    /**
     * 레코드를 토픽에 발행한다.
     *
     * @param key   파티션 라우팅에 사용되는 키 (null 가능)
     * @param value 메시지 본문
     * @return 기록 위치 메타데이터
     */
    public RecordMetadata send(String key, String value) {
        Record record = Record.of(key, value);
        return topic.append(record);
    }
}
