# ForkJoinPool 학습 커리큘럼

## 학습 목표

ForkJoinPool의 내부 동작을 **코드로 직접 읽고 실행**하며 이해한다.
"Work-Stealing이 뭔지 안다"에서 "왜 LIFO/FIFO 비대칭인지 설명할 수 있다"로 넘어가는 것이 목표다.

## 선수 지식

- Java 스레드 기초 (Thread, Runnable)
- synchronized, volatile 기초
- Deque 자료구조

## 목차

**Part I — 코드로 배우는 ForkJoinPool**
- 1. Work-Stealing 알고리즘의 핵심: 양방향 큐
- 2. 워커 스레드: 훔치거나 실행하거나
- 3. RecursiveTask: 분할정복으로 값 계산
- 4. RecursiveAction: 분할정복으로 배열 조작
- 5. ForkJoinPool 모니터링
- 6. ManagedBlocker: 블로킹과 병렬성 유지

---

# Part I: 코드로 배우는 ForkJoinPool

## 1. Work-Stealing 알고리즘의 핵심: 양방향 큐

> Work-Stealing의 핵심은 LIFO/FIFO 비대칭이다. 소유자는 최신 태스크부터, 도둑은 가장 오래된 태스크를 가져간다.

🧪 **관련 테스트**: [`WorkStealingDequeTest`](src/test/java/com/jiminkkk/forkjoin/chapter1/WorkStealingDequeTest.java) (7개 테스트)

### 1-1. 왜 deque인가?

Work-Stealing은 단방향 큐로는 구현할 수 없다. 소유자와 도둑이 **서로 다른 끝**에서 접근해야 하기 때문이다.

- 소유자 스레드: top에서 push/pop (LIFO)
- 다른 스레드(도둑): bottom에서 steal (FIFO)

이 비대칭이 중요한 이유: 도둑이 bottom에서 훔쳐가는 태스크는 가장 오래전에 fork된, 즉 가장 큰(다시 분할될 가능성이 높은) 태스크다. 따라서 훔쳐진 태스크는 다시 분할되어 풀 전체에 고르게 퍼진다.

📄 [`WorkStealingDeque.java:22`](src/main/java/com/jiminkkk/forkjoin/core/WorkStealingDeque.java) — LIFO/FIFO 비대칭 구조와 교육용 단순화 설명

🧪 [`WorkStealingDequeTest.java:26`](src/test/java/com/jiminkkk/forkjoin/chapter1/WorkStealingDequeTest.java) — push/pop이 LIFO 순서인지 검증

🧪 [`WorkStealingDequeTest.java:42`](src/test/java/com/jiminkkk/forkjoin/chapter1/WorkStealingDequeTest.java) — steal이 FIFO 순서인지 검증

> **자기 점검**: 소유자가 LIFO를 사용하는 이유는? 힌트: "캐시 지역성"과 "최근에 생성된 태스크"를 연결해라.

### 1-2. 실제 구현과 교육용 구현의 차이

실제 ForkJoinPool.WorkQueue는 CAS(Compare-And-Swap) 연산으로 lock-free하게 동작한다. 이 구현은 `synchronized`로 단순화했다.

실제: `volatile int top, base` + `ForkJoinTask<?>[] array` (circular buffer)
교육용: `ArrayDeque<T>` + `synchronized`

📄 [`WorkStealingDeque.java:40`](src/main/java/com/jiminkkk/forkjoin/core/WorkStealingDeque.java) — pop() 메서드의 주석에서 실제 CAS 동작 설명

🧪 [`WorkStealingDequeTest.java:88`](src/test/java/com/jiminkkk/forkjoin/chapter1/WorkStealingDequeTest.java) — 단일 원소에서의 경쟁 조건 테스트

> **자기 점검**: 큐에 원소가 1개일 때 소유자의 pop과 도둑의 steal이 동시에 발생하면 어떤 일이 벌어지는가? 실제 ForkJoinPool은 이를 CAS로 어떻게 해결하는가?

---

## 2. 워커 스레드: 훔치거나 실행하거나

> 각 워커는 자신의 큐를 먼저 확인하고, 비어 있으면 랜덤으로 선택한 다른 워커에서 steal한다.

### 2-1. 워커의 핵심 루프

```
while (실행 중) {
    task = 내 큐에서 pop()     // 최신 태스크부터
    if (task == null) {
        task = 다른 워커에서 steal()  // 큐가 가장 큰 워커 선택
    }
    if (task != null) {
        task.run()
    } else {
        Thread.yield()  // 실제: park/unpark
    }
}
```

📄 [`WorkerThread.java:61`](src/main/java/com/jiminkkk/forkjoin/core/WorkerThread.java) — 핵심 루프 구현

📄 [`WorkerThread.java:83`](src/main/java/com/jiminkkk/forkjoin/core/WorkerThread.java) — trySteal(): 큐 크기 기반 희생자 선택

> **자기 점검**: 이 구현은 "큐 크기가 가장 큰 워커"를 선택한다. 실제 ForkJoinPool은 기본적으로 랜덤 희생자를 선택한다. 각 전략의 장단점은 무엇인가? 힌트: 큐 크기 비교의 오버헤드와 steal 성공률을 생각해라.

---

## 3. RecursiveTask: 분할정복으로 값 계산

> RecursiveTask는 결과를 반환하는 분할정복 태스크다. 핵심 패턴은 "왼쪽 fork, 오른쪽 직접 compute, 왼쪽 join"이다.

🧪 **관련 테스트**: [`RecursiveTaskTest`](src/test/java/com/jiminkkk/forkjoin/chapter3/RecursiveTaskTest.java) (11개 테스트)

### 3-1. 올바른 fork/join 패턴

```java
leftTask.fork();                        // 비동기 예약
long rightResult = rightTask.compute(); // 현재 스레드에서 직접 실행
long leftResult = leftTask.join();      // 왼쪽 완료 대기
```

오른쪽을 `fork()` + `join()`으로 처리하지 않고 `compute()`를 직접 호출하는 이유:
현재 스레드를 유휴 상태로 놔두지 않기 위해서다. 두 태스크를 모두 fork하면 현재 스레드가 아무것도 안 하는 순간이 생긴다.

📄 [`SumTask.java:48`](src/main/java/com/jiminkkk/forkjoin/chapter3/SumTask.java) — fork/join 패턴과 주석

🧪 [`RecursiveTaskTest.java:37`](src/test/java/com/jiminkkk/forkjoin/chapter3/RecursiveTaskTest.java) — 병렬 결과가 순차 결과와 동일한지 검증

> **자기 점검**: `leftTask.fork(); rightTask.fork(); leftTask.join(); rightTask.join();` 패턴과 비교했을 때 위 패턴의 장단점은?

### 3-2. THRESHOLD: 분할을 멈추는 기준

태스크를 너무 잘게 쪼개면 오히려 오버헤드가 커진다. ForkJoinTask의 권장 크기는 100~10,000 기본 연산 단계다.

📄 [`SumTask.java:30`](src/main/java/com/jiminkkk/forkjoin/chapter3/SumTask.java) — THRESHOLD 상수와 설명

🧪 [`RecursiveTaskTest.java:58`](src/test/java/com/jiminkkk/forkjoin/chapter3/RecursiveTaskTest.java) — THRESHOLD보다 작은 배열은 직접 계산됨을 검증

> **자기 점검**: THRESHOLD를 1로 설정하면 어떻게 되는가? 10,000,000으로 설정하면?

### 3-3. FibonacciTask: 병렬화가 항상 빠르지 않다는 증거

📄 [`FibonacciTask.java`](src/main/java/com/jiminkkk/forkjoin/chapter3/FibonacciTask.java) — 교육 목적 비효율 구현과 이유

🧪 [`RecursiveTaskTest.java:100`](src/test/java/com/jiminkkk/forkjoin/chapter3/RecursiveTaskTest.java) — 정확성 검증

> **자기 점검**: fib(20)을 순차 계산하는 것과 fork/join으로 계산하는 것 중 무엇이 빠른가? 왜?

---

## 4. RecursiveAction: 분할정복으로 배열 조작

> RecursiveAction은 반환값 없이 배열을 in-place로 수정하는 태스크다. invokeAll()을 사용하면 fork()+join()을 더 간결하게 쓸 수 있다.

🧪 **관련 테스트**: [`RecursiveActionTest`](src/test/java/com/jiminkkk/forkjoin/chapter4/RecursiveActionTest.java) (9개 테스트)

### 4-1. invokeAll() vs fork() + join()

```java
// invokeAll 버전 (간결)
invokeAll(leftAction, rightAction);

// 동등한 fork/join 버전
leftAction.fork();
rightAction.compute(); // 오른쪽을 현재 스레드에서
leftAction.join();
```

`invokeAll(a, b)`는 내부적으로 b를 현재 스레드에서 실행하고 a를 join한다.

📄 [`MergeSortAction.java:55`](src/main/java/com/jiminkkk/forkjoin/chapter4/MergeSortAction.java) — invokeAll 사용과 주석

🧪 [`RecursiveActionTest.java:47`](src/test/java/com/jiminkkk/forkjoin/chapter4/RecursiveActionTest.java) — 대규모 배열에서 Arrays.sort와 결과 동일성 검증

> **자기 점검**: RecursiveAction에서 결과를 "반환"하는 대신 어떻게 데이터를 전달하는가?

### 4-2. ArrayInitAction: 병렬 배열 초기화

📄 [`ArrayInitAction.java`](src/main/java/com/jiminkkk/forkjoin/chapter4/ArrayInitAction.java) — IntUnaryOperator로 유연한 초기화

🧪 [`RecursiveActionTest.java:79`](src/test/java/com/jiminkkk/forkjoin/chapter4/RecursiveActionTest.java) — generator 함수 적용 검증

> **자기 점검**: `Arrays.parallelSetAll()`은 내부적으로 어떻게 구현되어 있을까?

---

## 5. ForkJoinPool 모니터링

> ForkJoinPool의 모니터링 메서드들은 대부분 추정치(estimate)다. 정확한 스냅샷보다 경향을 보는 데 사용한다.

🧪 **관련 테스트**: [`PoolMonitorTest`](src/test/java/com/jiminkkk/forkjoin/chapter5/PoolMonitorTest.java) (6개 테스트)

### 5-1. 핵심 모니터링 메서드

| 메서드 | 의미 | 주의 |
|--------|------|------|
| `getParallelism()` | 목표 병렬 처리 수준 | 정확한 값 |
| `getActiveThreadCount()` | 실행 중인 스레드 수 | 추정치 |
| `getStealCount()` | work-stealing 횟수 누적 | 추정치, 과소 추정 가능 |
| `getPoolSize()` | 생성된 워커 스레드 수 | 정확한 값 |
| `getQueuedTaskCount()` | 큐 대기 태스크 수 | 추정치 |

📄 [`PoolMonitor.java:40`](src/main/java/com/jiminkkk/forkjoin/chapter5/PoolMonitor.java) — snapshot() 메서드

📄 [`PoolMonitor.java:63`](src/main/java/com/jiminkkk/forkjoin/chapter5/PoolMonitor.java) — PoolSnapshot record

🧪 [`PoolMonitorTest.java:51`](src/test/java/com/jiminkkk/forkjoin/chapter5/PoolMonitorTest.java) — PoolMonitor 스냅샷 검증

> **자기 점검**: `getStealCount()`가 과소 추정될 수 있는 이유는? 풀이 활성 상태일 때 각 워커의 stealCount를 언제 집계하는가?

### 5-2. commonPool vs 커스텀 풀

```java
// commonPool: JVM 전역 공유, shutdown() 불가
ForkJoinPool.commonPool()

// 커스텀 풀: try-finally로 반드시 shutdown
ForkJoinPool pool = new ForkJoinPool(parallelism);
try { ... } finally { pool.shutdown(); }
```

🧪 [`PoolMonitorTest.java:32`](src/test/java/com/jiminkkk/forkjoin/chapter5/PoolMonitorTest.java) — 커스텀 parallelism 설정 검증

> **자기 점검**: commonPool을 shutdown()하면 어떻게 되는가? 왜 commonPool은 shutdown이 금지되어 있는가?

---

## 6. ManagedBlocker: 블로킹과 병렬성 유지

> ForkJoinPool 워커가 I/O 대기로 블로킹되면 병렬성이 떨어진다. ManagedBlocker는 이를 풀에게 알려 보상 스레드를 생성하게 한다.

🧪 **관련 테스트**: [`ManagedBlockerTest`](src/test/java/com/jiminkkk/forkjoin/chapter6/ManagedBlockerTest.java) (6개 테스트)

### 6-1. ManagedBlocker 프로토콜

```java
// ForkJoinPool.managedBlock() 내부 동작:
while (!blocker.isReleasable()) {
    blocker.block();
}
```

- `isReleasable()`: 논-블로킹으로 조건 확인. true면 block() 스킵
- `block()`: 실제 블로킹 수행. 완료되면 true 반환

핵심: `managedBlock()`을 호출하면 ForkJoinPool이 "이 스레드가 곧 블로킹된다"는 것을 알고, 필요하면 보상 스레드를 생성하여 목표 병렬성을 유지한다.

📄 [`SimulatedIOBlocker.java:50`](src/main/java/com/jiminkkk/forkjoin/chapter6/SimulatedIOBlocker.java) — isReleasable() 구현

📄 [`SimulatedIOBlocker.java:62`](src/main/java/com/jiminkkk/forkjoin/chapter6/SimulatedIOBlocker.java) — block() 구현

🧪 [`ManagedBlockerTest.java:23`](src/test/java/com/jiminkkk/forkjoin/chapter6/ManagedBlockerTest.java) — 초기 상태 검증

🧪 [`ManagedBlockerTest.java:48`](src/test/java/com/jiminkkk/forkjoin/chapter6/ManagedBlockerTest.java) — ForkJoinPool.managedBlock() 완료 검증

> **자기 점검**: ManagedBlocker를 쓰지 않고 ForkJoinTask 안에서 Thread.sleep()을 직접 호출하면 어떤 문제가 생기는가?

### 6-2. 보상 스레드와 최대 스레드 수

블로킹 태스크가 많을수록 보상 스레드가 많이 생성된다. 실제 ForkJoinPool의 최대 워커 스레드 수는 `Short.MAX_VALUE` (32767)로 제한된다. 이 한도는 내부 비트필드로 관리된다.

🧪 [`ManagedBlockerTest.java:72`](src/test/java/com/jiminkkk/forkjoin/chapter6/ManagedBlockerTest.java) — 여러 블로킹 태스크 병렬 실행 검증

> **자기 점검**: 보상 스레드가 무한정 생성된다면 어떤 문제가 생기는가? 왜 최대 256개로 제한하는가?

---

## 전체 학습 체크리스트

- [ ] WorkStealingDeque의 LIFO/FIFO 비대칭을 코드로 확인했는가
- [ ] `fork()` + `compute()` + `join()` 패턴이 `fork()` + `fork()` + `join()` + `join()`보다 나은 이유를 설명할 수 있는가
- [ ] THRESHOLD가 성능에 미치는 영향을 이해하는가
- [ ] invokeAll()이 내부적으로 하는 일을 설명할 수 있는가
- [ ] commonPool과 커스텀 풀의 차이를 설명할 수 있는가
- [ ] ManagedBlocker 없이 ForkJoinTask 안에서 블로킹하면 왜 나쁜지 설명할 수 있는가
