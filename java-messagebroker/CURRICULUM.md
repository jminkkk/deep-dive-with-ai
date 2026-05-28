# Message Broker 학습 커리큘럼

## 학습 목표

Kafka의 내부 동작을 **코드로 직접 읽고 실행**하며 이해한다.
"Kafka는 분산 메시지 큐다"에서 "왜 같은 키는 같은 파티션으로 가야 하는가?", "at-least-once와 at-most-once의 차이는 commit 시점 하나다"로 넘어가는 것이 목표다.

## 선수 지식

- Java 인터페이스, 제네릭 기초
- List, Map 컬렉션 사용
- 예외(Exception) 기초

## 목차

**Part I — 코드로 배우는 Kafka 핵심 개념**
- 1. CommitLog: Kafka의 핵심 자료구조
- 2. Topic & Partition: 분산과 순서의 트레이드오프
- 3. Producer: 메시지 발행과 파티션 라우팅
- 4. Consumer & Offset: 메시지 소비와 위치 추적
- 5. Consumer Group: 부하 분산과 Rebalance
- 6. Delivery Semantics: 유실 vs 중복, commit 시점이 전부다

---

# Part I: 코드로 배우는 Kafka 핵심 개념

## 1. CommitLog: Kafka의 핵심 자료구조

> Kafka는 데이터베이스도 큐도 아니다. **추가 전용 로그(Append-Only Log)** 다.

🧪 **관련 테스트**: [`CommitLogTest`](src/test/java/com/jiminkkk/messagebroker/chapter1/CommitLogTest.java) (6개 테스트)

### 1-1. Append-Only란 무엇인가

일반 데이터베이스는 레코드를 삽입/수정/삭제할 수 있다. Kafka의 CommitLog는 **오직 끝에 추가(append)만 가능**하다. 한 번 기록된 데이터는 변경되지 않는다.

이 단순한 제약이 왜 장점이 되는가:
- **순차 쓰기**: 디스크의 특정 위치를 찾는 랜덤 I/O가 없어 HDD에서도 빠르다
- **Replay 가능**: 과거 어느 시점이든 다시 읽을 수 있다
- **단순한 동시성**: 쓰기는 끝에만 하므로 경쟁이 최소화된다

📄 [`CommitLog.java:43`](src/main/java/com/jiminkkk/messagebroker/core/CommitLog.java) — append() 구현: 항상 끝에만 추가

🧪 [`CommitLogTest.java:31`](src/test/java/com/jiminkkk/messagebroker/chapter1/CommitLogTest.java) — offset이 순서대로 부여되는지 검증

> **자기 점검**: 데이터베이스의 UPDATE가 없다면, Kafka에서 "상태 변경"을 어떻게 표현하는가?
> 힌트: 같은 key에 새 value를 append하면 어떻게 될까.

### 1-2. Offset: 로그의 주소

각 레코드는 **offset**이라는 고유한 위치값을 가진다. 0부터 시작하여 append할 때마다 1씩 증가한다. offset을 알면 해당 레코드를 O(1)로 찾을 수 있다.

실제 Kafka는 세그먼트 파일(segment file)과 인덱스 파일로 "offset → 파일 내 바이트 위치"를 관리한다. 이 구현은 ArrayList로 단순화했다.

📄 [`CommitLog.java:55`](src/main/java/com/jiminkkk/messagebroker/core/CommitLog.java) — read(fromOffset, maxRecords) 구현

🧪 [`CommitLogTest.java:43`](src/test/java/com/jiminkkk/messagebroker/chapter1/CommitLogTest.java) — offset 기반 read 검증

🧪 [`CommitLogTest.java:57`](src/test/java/com/jiminkkk/messagebroker/chapter1/CommitLogTest.java) — Replay: 같은 offset으로 여러 번 읽기

> **자기 점검**: Kafka Consumer가 "어디까지 읽었는지"를 추적할 때 offset을 사용한다.
> offset을 브로커에 저장하면 어떤 장점이 있는가? 클라이언트에 저장하면?

---

## 2. Topic & Partition: 분산과 순서의 트레이드오프

> 파티션이 많을수록 병렬성은 높아지지만, **전체 순서는 포기해야 한다**.

🧪 **관련 테스트**: [`PartitionTest`](src/test/java/com/jiminkkk/messagebroker/chapter2/PartitionTest.java) (5개 테스트)

### 2-1. 왜 파티션으로 나누는가

단일 로그로는 처리량에 한계가 있다. 토픽을 여러 파티션으로 나누면:
- **쓰기**: 여러 브로커에 병렬로 기록 가능
- **읽기**: 여러 Consumer가 각자 담당 파티션을 독립적으로 읽음
- **확장**: 파티션 수 = 최대 병렬 Consumer 수

📄 [`Topic.java:52`](src/main/java/com/jiminkkk/messagebroker/core/Topic.java) — Partitioner가 파티션을 결정하는 흐름

🧪 [`PartitionTest.java:24`](src/test/java/com/jiminkkk/messagebroker/chapter2/PartitionTest.java) — 같은 키는 항상 같은 파티션으로 라우팅됨을 검증

> **자기 점검**: 파티션 수를 늘리면 병렬성이 높아지는데, 왜 무한정 늘리지 않는가?
> 힌트: 브로커 장애 시 리더 선출, 메타데이터 크기를 생각해라.

### 2-2. 키 기반 라우팅과 순서 보장

```
같은 키 → hash(key) % numPartitions → 항상 같은 파티션 → 파티션 내 순서 보장
```

"user-42의 모든 이벤트가 순서대로 처리되어야 한다"면: user-42를 키로 사용하면 된다. 단, 이 순서 보장은 **파티션 내에서만** 유효하다. 파티션 간 순서는 보장되지 않는다.

📄 [`HashPartitioner.java:30`](src/main/java/com/jiminkkk/messagebroker/core/HashPartitioner.java) — 키 해시 → 파티션 계산

🧪 [`PartitionTest.java:40`](src/test/java/com/jiminkkk/messagebroker/chapter2/PartitionTest.java) — 다른 키는 다른 파티션으로 분산될 수 있음

> **자기 점검**: 키가 null이면 어떤 파티션으로 가는가?
> 키 없는 레코드에서 순서를 보장할 수 없는 이유는?

---

## 3. Producer: 메시지 발행과 파티션 라우팅

> Producer는 "어느 파티션에 쓸지"를 결정하고 브로커에 전달한다.

🧪 **관련 테스트**: [`ProducerTest`](src/test/java/com/jiminkkk/messagebroker/chapter3/ProducerTest.java) (5개 테스트)

### 3-1. send()의 반환값 — RecordMetadata

`send(key, value)`는 레코드를 발행하고 **어디에 기록됐는지** 알려주는 RecordMetadata를 반환한다.

```java
RecordMetadata meta = producer.send("user-42", "login");
// meta.topic()     → "user-events"
// meta.partition() → 2  (hash("user-42") % numPartitions)
// meta.offset()    → 5  (파티션 2의 5번째 레코드)
```

📄 [`Producer.java:45`](src/main/java/com/jiminkkk/messagebroker/producer/Producer.java) — send() 구현

🧪 [`ProducerTest.java:32`](src/test/java/com/jiminkkk/messagebroker/chapter3/ProducerTest.java) — RecordMetadata 검증

> **자기 점검**: 실제 Kafka Producer에서 `acks=all`은 무엇을 의미하는가?
> 왜 `acks=1`보다 느리지만 더 안전한가?

### 3-2. 실제 Kafka Producer의 Batching (교육용 생략 항목)

이 구현은 1건씩 즉시 전송하지만, 실제 Kafka Producer는 레코드를 **배치(batch)** 로 묶어 전송한다. `linger.ms`(최대 대기 시간)와 `batch.size`(최대 배치 크기) 설정이 처리량과 지연의 트레이드오프를 결정한다.

🧪 [`ProducerTest.java:57`](src/test/java/com/jiminkkk/messagebroker/chapter3/ProducerTest.java) — 대량 발행 시 손실 없음 검증

> **자기 점검**: `linger.ms=0`(기본값)과 `linger.ms=5`의 차이는?
> 어떤 상황에서 linger.ms를 늘리는 것이 유리한가?

---

## 4. Consumer & Offset: 메시지 소비와 위치 추적

> Consumer의 핵심은 두 개의 offset이다 — **currentOffset**과 **committedOffset**.

🧪 **관련 테스트**: [`ConsumerTest`](src/test/java/com/jiminkkk/messagebroker/chapter4/ConsumerTest.java) (5개 테스트)

### 4-1. currentOffset vs committedOffset

```
currentOffset  : 다음에 읽을 위치. poll()마다 전진.
committedOffset: "여기까지 처리 완료"를 브로커에 알린 위치.
                 장애 복구 시 이 위치부터 재처리 시작.
```

두 offset의 간격(lag)이 클수록 장애 시 재처리해야 할 데이터가 많다.

📄 [`Consumer.java:53`](src/main/java/com/jiminkkk/messagebroker/consumer/Consumer.java) — poll(): currentOffset만 전진, committedOffset 불변

📄 [`Consumer.java:72`](src/main/java/com/jiminkkk/messagebroker/consumer/Consumer.java) — commitSync(): committedOffset을 currentOffset으로 갱신

🧪 [`ConsumerTest.java:33`](src/test/java/com/jiminkkk/messagebroker/chapter4/ConsumerTest.java) — poll() 후 currentOffset 변화 검증

🧪 [`ConsumerTest.java:48`](src/test/java/com/jiminkkk/messagebroker/chapter4/ConsumerTest.java) — commitSync() 전후 committedOffset 변화 검증

> **자기 점검**: `enable.auto.commit=true`(Kafka 기본값)이면 어떤 문제가 생길 수 있는가?
> auto.commit은 at-least-once인가, at-most-once인가?

### 4-2. seek()과 Replay

📄 [`Consumer.java:82`](src/main/java/com/jiminkkk/messagebroker/consumer/Consumer.java) — seek(): 임의 위치로 이동

🧪 [`ConsumerTest.java:62`](src/test/java/com/jiminkkk/messagebroker/chapter4/ConsumerTest.java) — seek(0)으로 처음부터 Replay

> **자기 점검**: Kafka의 `auto.offset.reset=earliest`는 무엇을 의미하는가?
> Consumer가 처음으로 그룹에 참여할 때 어느 offset부터 읽기 시작하는가?

---

## 5. Consumer Group: 부하 분산과 Rebalance

> 하나의 파티션은 그룹 내 **단 하나의 Consumer**에만 할당된다.

🧪 **관련 테스트**: [`ConsumerGroupTest`](src/test/java/com/jiminkkk/messagebroker/chapter5/ConsumerGroupTest.java) (5개 테스트)

### 5-1. 파티션 할당 규칙

```
파티션 수 > Consumer 수: 일부 Consumer가 여러 파티션 담당
파티션 수 = Consumer 수: 1:1 매핑 (이상적)
파티션 수 < Consumer 수: 초과 Consumer는 놀게 됨
```

이 규칙 때문에 **파티션 수가 최대 병렬 처리 단위**가 된다. Consumer를 아무리 늘려도 파티션 수 이상으로 병렬화되지 않는다.

📄 [`ConsumerGroup.java:61`](src/main/java/com/jiminkkk/messagebroker/consumer/ConsumerGroup.java) — rebalance(): 라운드 로빈 파티션 할당

🧪 [`ConsumerGroupTest.java:28`](src/test/java/com/jiminkkk/messagebroker/chapter5/ConsumerGroupTest.java) — 파티션 균등 분배 검증

🧪 [`ConsumerGroupTest.java:54`](src/test/java/com/jiminkkk/messagebroker/chapter5/ConsumerGroupTest.java) — Consumer 수 > 파티션 수: 초과 Consumer idle 검증

> **자기 점검**: Consumer Group을 두 개(group-A, group-B) 만들어 같은 토픽을 구독하면 어떻게 되는가?
> group-A와 group-B는 서로 영향을 주는가?

### 5-2. Rebalance: Consumer 변동 시 재분배

Consumer가 추가되거나 제거되면 **Rebalance**가 발생한다. Rebalance 중에는 모든 Consumer가 일시 중단되고 파티션이 재분배된다(Stop-The-World).

실제 Kafka의 Rebalance 프로토콜: JoinGroup → SyncGroup → 할당 완료. 이 구현은 직접 재할당으로 단순화했다.

📄 [`ConsumerGroup.java:43`](src/main/java/com/jiminkkk/messagebroker/consumer/ConsumerGroup.java) — join(): Consumer 추가 + rebalance 트리거

🧪 [`ConsumerGroupTest.java:41`](src/test/java/com/jiminkkk/messagebroker/chapter5/ConsumerGroupTest.java) — Consumer 제거 후 Rebalance 검증

> **자기 점검**: Rebalance가 잦으면 어떤 문제가 생기는가?
> `session.timeout.ms`와 `heartbeat.interval.ms`는 Rebalance와 어떻게 연관되는가?

---

## 6. Delivery Semantics: 유실 vs 중복, commit 시점이 전부다

> at-least-once냐 at-most-once냐는 `commitSync()`를 **처리 전**에 하느냐 **처리 후**에 하느냐로 결정된다.

🧪 **관련 테스트**: [`DeliverySemanticsTest`](src/test/java/com/jiminkkk/messagebroker/chapter6/DeliverySemanticsTest.java) (4개 테스트)

### 6-1. 세 가지 전달 보장

```
At-Most-Once:   poll → commit → process  (처리 전 commit → 실패 시 유실)
At-Least-Once:  poll → process → commit  (처리 후 commit → 실패 시 재처리)
Exactly-Once:   처리와 commit을 원자적으로 (Kafka Transactions 필요)
```

📄 [`DeliverySemantics.java:33`](src/main/java/com/jiminkkk/messagebroker/chapter6/DeliverySemantics.java) — atMostOnce(): commit 먼저

📄 [`DeliverySemantics.java:51`](src/main/java/com/jiminkkk/messagebroker/chapter6/DeliverySemantics.java) — atLeastOnce(): commit 나중

🧪 [`DeliverySemanticsTest.java:36`](src/test/java/com/jiminkkk/messagebroker/chapter6/DeliverySemanticsTest.java) — at-most-once: 처리 실패 시 유실 확인

🧪 [`DeliverySemanticsTest.java:62`](src/test/java/com/jiminkkk/messagebroker/chapter6/DeliverySemanticsTest.java) — at-least-once: 실패 후 재처리 확인

> **자기 점검**: "결제 처리" 서비스에 어떤 전달 보장이 적합한가? 중복 결제를 막으려면 추가로 무엇이 필요한가?

### 6-2. At-Least-Once + 멱등성 = Exactly-Once

완전한 Exactly-Once는 Kafka Transactions(Producer transactional.id + Consumer isolation.level)로 구현한다. 실무에서 더 단순한 대안은:
- **멱등적 처리**: 같은 레코드를 두 번 처리해도 결과가 같도록 설계 (중복 결제 대신 "이미 처리된 결제"를 체크)
- **Idempotent Producer**: `enable.idempotence=true`로 Producer 재시도 시 중복 기록 방지

📄 [`DeliverySemantics.java:64`](src/main/java/com/jiminkkk/messagebroker/chapter6/DeliverySemantics.java) — atLeastOnceWithRollback(): 실패 시 seekToCommitted

🧪 [`DeliverySemanticsTest.java:102`](src/test/java/com/jiminkkk/messagebroker/chapter6/DeliverySemanticsTest.java) — rollback 후 committedOffset 불변 확인

> **자기 점검**: `seekToCommitted()`로 되돌아가면 at-least-once가 보장된다. 하지만 같은 레코드를 두 번 처리할 수 있다. 이 중복을 DB 레벨에서 어떻게 막을 수 있는가?

---

## 전체 학습 체크리스트

- [ ] Append-Only Log가 왜 빠른지, 왜 Replay가 가능한지 설명할 수 있는가
- [ ] 같은 키를 써야 순서가 보장되는 이유를 파티션 구조로 설명할 수 있는가
- [ ] currentOffset과 committedOffset의 차이를 장애 복구 시나리오로 설명할 수 있는가
- [ ] at-most-once와 at-least-once의 차이가 commit 시점에 있음을 코드로 확인했는가
- [ ] Consumer Group에서 파티션 수가 최대 병렬화 단위인 이유를 설명할 수 있는가
- [ ] Rebalance가 언제 발생하고 왜 비용이 큰지 설명할 수 있는가
