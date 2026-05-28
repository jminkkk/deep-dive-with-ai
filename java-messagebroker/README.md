# Message Broker from Scratch

> Kafka의 핵심 원리를 **코드로 직접 구현**하며 이해하는 교육용 프로젝트.

## 무엇을 배울 수 있나

| 챕터 | 핵심 코드 | 테스트 |
|------|---------|-------|
| 1. Append-Only CommitLog | [CommitLog.java](src/main/java/com/jiminkkk/messagebroker/core/CommitLog.java) | 6개 |
| 2. Topic & Partition | [Topic.java](src/main/java/com/jiminkkk/messagebroker/core/Topic.java), [HashPartitioner.java](src/main/java/com/jiminkkk/messagebroker/core/HashPartitioner.java) | 5개 |
| 3. Producer | [Producer.java](src/main/java/com/jiminkkk/messagebroker/producer/Producer.java) | 5개 |
| 4. Consumer & Offset | [Consumer.java](src/main/java/com/jiminkkk/messagebroker/consumer/Consumer.java) | 5개 |
| 5. Consumer Group & Rebalance | [ConsumerGroup.java](src/main/java/com/jiminkkk/messagebroker/consumer/ConsumerGroup.java) | 5개 |
| 6. Delivery Semantics | [DeliverySemantics.java](src/main/java/com/jiminkkk/messagebroker/chapter6/DeliverySemantics.java) | 4개 |

## 30초 체험

```bash
git clone <repo>
cd java-messagebroker
./gradlew test
```

테스트가 통과하면 각 파일을 열어서 CURRICULUM.md의 순서대로 읽어라.

## 학습 시작하기

**[📖 CURRICULUM.md](CURRICULUM.md)를 따라가는 것을 권장한다.**

각 챕터는 "개념 설명 → 구현 코드 → 테스트 → 자기 점검"의 흐름으로 구성되어 있다.

## 구현 범위와 한계

| 항목 | 상태 | 비고 |
|------|------|------|
| Append-Only CommitLog | ✅ | in-memory (실제는 세그먼트 파일 + 인덱스) |
| Topic & Partition 모델 | ✅ | 핵심 구조 구현 |
| 키 기반 Hash Partitioner | ✅ | 실제 Kafka DefaultPartitioner와 동일 원리 |
| Producer (send + routing) | ✅ | 단순화: 배치/압축/acks 생략 |
| Consumer (poll + offset) | ✅ | currentOffset / committedOffset 구분 |
| Consumer Group & Rebalance | ✅ | 단순화: RoundRobin 할당, Stop-The-World |
| Delivery Semantics | ✅ | at-most-once / at-least-once / rollback |
| 네트워크 / 직렬화 | ❌ | 교육 목적으로 in-memory |
| 복제 (Replication) | ❌ | ISR, Leader/Follower 생략 |
| Kafka Transactions | ❌ | Exactly-Once는 개념만 설명 |
| Log Compaction | ❌ | 스코프 외 |

## 참고

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kafka: The Definitive Guide (O'Reilly)](https://www.confluent.io/resources/kafka-the-definitive-guide/)
- [Designing Data-Intensive Applications — Chapter 11: Stream Processing](https://dataintensive.net/)
