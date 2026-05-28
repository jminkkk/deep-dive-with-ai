package com.jiminkkk.messagebroker.chapter6;

import com.jiminkkk.messagebroker.consumer.Consumer;
import com.jiminkkk.messagebroker.core.Topic;
import com.jiminkkk.messagebroker.model.Record;

import java.util.List;

/**
 * Kafka의 세 가지 전달 보장(Delivery Semantics) 구현 예시.
 *
 * <p>전달 보장은 commit 시점으로 결정된다. commit이란 "여기까지 처리했다"고
 * 브로커에 알리는 행위다. 장애 발생 시 committed offset부터 재처리된다.
 *
 * <pre>
 * 전략              commit 시점              특성
 * At-Most-Once    poll 직후 (처리 전)       유실 가능
 * At-Least-Once   처리 완료 후              중복 가능
 * Exactly-Once    처리와 commit을 원자적으로  유실·중복 없음
 * </pre>
 *
 * @see <a href="https://kafka.apache.org/documentation/#semantics">Message Delivery Semantics</a>
 */
public class DeliverySemantics {

    /**
     * At-Most-Once: poll 직후 commit → 처리 실패 시 메시지 유실.
     *
     * <p>처리 전에 commit했으므로 장애 발생 시 다음 poll은 그 다음 offset부터 시작.
     * 유실된 레코드는 다시 읽을 수 없다.
     *
     * <p>사용 사례: 로그 수집처럼 일부 유실이 허용되는 경우.
     */
    public static void atMostOnce(Consumer consumer, Topic topic,
                                   java.util.function.Consumer<Record> processor) {
        List<Record> records = consumer.poll(topic, 100);
        consumer.commitSync();         // ← 처리 전에 commit
        for (Record record : records) {
            processor.accept(record);  // 여기서 실패해도 이미 commit됨 → 유실
        }
    }

    /**
     * At-Least-Once: 처리 완료 후 commit → 처리 실패 시 재처리(중복 가능).
     *
     * <p>처리 후 commit했으므로 장애 발생 시 committed offset부터 재처리.
     * 같은 레코드를 두 번 처리할 수 있다.
     *
     * <p>사용 사례: 결제, 주문처럼 유실이 허용되지 않는 경우.
     * 처리 로직이 멱등적이거나 중복 체크를 별도로 구현해야 한다.
     */
    public static void atLeastOnce(Consumer consumer, Topic topic,
                                    java.util.function.Consumer<Record> processor) {
        List<Record> records = consumer.poll(topic, 100);
        for (Record record : records) {
            processor.accept(record);  // 처리 완료 후
        }
        consumer.commitSync();         // ← 모든 처리 후 commit
    }

    /**
     * At-Least-Once with rollback: 처리 중 예외 발생 시 committed offset으로 되돌아감.
     *
     * <p>실패한 레코드부터 다시 처리하기 위해 seekToCommitted()를 사용한다.
     */
    public static void atLeastOnceWithRollback(Consumer consumer, Topic topic,
                                                java.util.function.Consumer<Record> processor) {
        List<Record> records = consumer.poll(topic, 100);
        try {
            for (Record record : records) {
                processor.accept(record);
            }
            consumer.commitSync();
        } catch (Exception e) {
            consumer.seekToCommitted(); // ← 실패 시 마지막 commit 위치로 되돌아감
            throw e;
        }
    }
}
