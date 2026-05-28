package com.jiminkkk.messagebroker.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka 기본 파티셔너 — 키 해시 기반.
 *
 * <p>키가 있으면 키의 해시값으로 파티션을 결정한다.
 * 같은 키는 항상 같은 파티션으로 라우팅되므로 키 기준 순서가 보장된다.
 *
 * <p>키가 없으면 라운드 로빈으로 파티션을 순환한다.
 * (실제 Kafka 2.4+ 기본값은 sticky partitioner이지만, 교육 목적으로 round-robin 사용)
 *
 * <p>핵심: 같은 키 → 같은 파티션 → 순서 보장
 *
 * @see <a href="https://kafka.apache.org/documentation/#design_loadbalancing">Load Balancing</a>
 */
public class HashPartitioner implements Partitioner {

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    @Override
    public int partition(String key, int numPartitions) {
        if (key == null) {
            // 키 없음 → 라운드 로빈
            return Math.abs(roundRobinCounter.getAndIncrement() % numPartitions);
        }
        // 키 있음 → 해시 기반 (항상 같은 파티션)
        return Math.abs(key.hashCode() % numPartitions);
    }
}
