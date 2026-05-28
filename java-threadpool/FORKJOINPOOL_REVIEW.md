# ForkJoinPool Review Sheet

---

## 1. Work-Stealing & LIFO/FIFO 비대칭

ForkJoinPool의 각 워커 쓰레드는 **WorkStealingDeque**를 가진다.
소유자와 도둑 쓰레드가 서로 다른 끝에서 꺼내는 비대칭 구조다.

| 주체 | 방향 | 이유 |
|------|------|------|
| 소유자 쓰레드 | LIFO (최근 것부터) | 최근 fork된 태스크일수록 CPU 캐시 히트율 높음 + join 대기 최소화 |
| 도둑 쓰레드 | FIFO (오래된 것부터) | 오래된 태스크일수록 크기가 크다 → 훔치기 비용 대비 실행 시간이 길어 효율적 |

**비대칭의 목적**: 소유자와 도둑 간의 락 경합 최소화

---

## 2. fork + compute + join vs fork + fork + join + join

```java
// 패턴 A (비추천)
left.fork();
right.fork();   // right를 큐에 넣고
left.join();    // 대기 (work-stealing 하지만 오버헤드 있음)
right.join();

// 패턴 B (추천)
left.fork();
right.compute(); // 현재 쓰레드가 right를 직접 실행
left.join();
```

**패턴 B가 나은 이유**:
- `right`를 큐에 넣지 않음 → 큐 접근 비용 없음
- 현재 쓰레드가 직접 실행 → 컨텍스트 스위칭 없음
- `right` 완료 후 `left.join()` → left도 이미 완료됐을 가능성 높음

> `join()`이 내부적으로 work-stealing을 하긴 하지만, 큐를 경유하지 않는 `compute()`가 항상 더 저렴하다.

---

## 3. invokeAll() 내부 동작

```java
ForkJoinTask.invokeAll(t1, t2);
// 내부:
t2.fork();
t1.invoke();  // 현재 쓰레드가 직접 실행
t2.join();

ForkJoinTask.invokeAll(t1, t2, t3);
// 내부:
t1.fork();
t2.fork();
t3.invoke();  // 마지막만 현재 쓰레드가 직접 실행
t2.join();    // 역순 join (LIFO)
t1.join();
```

**핵심**: 항상 마지막 태스크는 현재 쓰레드가 직접 실행 → fork + compute + join 패턴 그대로

---

## 4. THRESHOLD 설정

```java
if (size <= THRESHOLD) {
    // 직접 처리
} else {
    // fork로 분할
}
```

| 상황 | 결과 |
|------|------|
| THRESHOLD 너무 작음 | fork 비용 > 연산 비용 → 오버헤드 지배 |
| THRESHOLD 너무 큼 | fork 거의 안 함 → 병렬성 미활용, 단일 쓰레드와 다름없음 |

**실무 기준**: `배열 크기 / (CPU 코어 수 × 4)`
코어 수의 4배로 나누는 이유: work-stealing이 균등하게 분배되도록 태스크를 충분히 쪼개기 위해

---

## 5. commonPool vs 커스텀 풀

| | commonPool | 커스텀 풀 |
|---|---|---|
| 공유 범위 | JVM 전역 (병렬스트림, 라이브러리 등 모두 공유) | 내 태스크만 격리 |
| shutdown | 불가 (JVM이 관리) | 직접 관리 (안 하면 리소스 누수) |
| 병렬성 | CPU 코어 수 - 1 | 직접 설정 |
| 격리 | 불가 | 가능 |

```java
// 위험: 다른 코드가 commonPool을 점유하면 내 스트림도 느려짐
list.parallelStream().map(...).collect(...);

// 안전: 격리된 풀에서 실행
ForkJoinPool pool = new ForkJoinPool(4);
pool.submit(() -> list.parallelStream().map(...).collect(...)).get();
```

---

## 6. ManagedBlocker

### 역할

ForkJoinPool 워커 쓰레드가 블로킹 작업을 시작하기 **직전**에 ForkJoinPool에게 알려주는 메커니즘.
ForkJoinPool은 이 신호를 받고 **보상 쓰레드(compensation thread)** 를 생성해 병렬성을 유지한다.

```
워커 쓰레드가 태스크 실행 중
        ↓
블로킹 직전 → ForkJoinPool.managedBlock(blocker) 호출
        ↓
ForkJoinPool: 보상 쓰레드 생성
        ↓
워커 쓰레드: block() 실행 → 블로킹
보상 쓰레드: 큐의 다음 태스크 처리
```

### ManagedBlocker 인터페이스

```java
public interface ManagedBlocker {
    boolean isReleasable(); // 블로킹 없이 바로 진행해도 되면 true (논블로킹 체크)
    boolean block() throws InterruptedException; // 실제 블로킹 작업
}
```

### ForkJoinPool 내부 루프

```java
while (!blocker.isReleasable()) {
    blocker.block();
}
```

### isReleasable() 의미

> **"블로킹 없이 바로 결과 줄 수 있어?"**

- `true` → block() 호출 스킵 (이미 완료됐거나 캐시 히트)
- `false` → block() 호출 (블로킹 필요)

```java
// 캐시 히트면 block() 자체를 건너뜀
@Override
public boolean isReleasable() {
    result = cache.get(key);
    return result != null;
}
```

### 왜 while 루프인가?

`block()`은 완료를 보장하지 않는다. `Object.wait()` 같은 경우 **spurious wakeup**(가짜 깨어남)이 발생할 수 있어 루프가 안전장치 역할을 한다.

```java
@Override
public boolean block() throws InterruptedException {
    synchronized (lock) {
        lock.wait(); // 조건 미충족인데 깨어날 수 있음
    }
    return isReleasable(); // 진짜 완료됐는지 재확인
}
```

### ManagedBlocker 없이 블로킹하면 왜 나쁜가

ForkJoinPool의 전제: **항상 parallelism 수만큼 쓰레드가 일하고 있어야 한다.**

```
ManagedBlocker 없이 블로킹:
  워커 쓰레드 blocked → 활성 쓰레드 수 < parallelism
  큐에 태스크가 남아있어도 처리 못 함
  → CPU는 노는데 태스크는 쌓임 → 처리량 감소
  → 최악의 경우: 모든 쓰레드가 블로킹 → 큐의 태스크 영원히 실행 안 됨 (데드락)
```

### 데드락 시나리오 (테스트로 확인)

```java
// parallelism=2, 쓰레드 2개
// 태스크 A, B: gate를 기다리며 쓰레드 점유 (ManagedBlocker 없이)
// 태스크 C: gate를 열어줄 태스크

// A, B → 쓰레드 2개 모두 점유
// C    → 실행할 쓰레드 없음 → 큐에서 영원히 대기
// gate → 열리지 않음 → 데드락
```

```java
// ManagedBlocker 사용 시:
// A, B가 managedBlock() 호출 → 보상 쓰레드 생성
// 보상 쓰레드가 C 실행 → gate 해제 → A, B 완료
```

### 보상 쓰레드 생명주기

```
IO 블로킹 → 보상 쓰레드 생성
        ↓
IO 완료 → 원래 워커 + 보상 쓰레드 잠깐 공존
        ↓
큐가 비면 → 유휴 대기
        ↓
keepAlive 시간 후 → 보상 쓰레드 제거
```

### 보상 쓰레드 무한 생성의 문제

| 문제 | 설명 |
|------|------|
| 메모리 고갈 | 쓰레드 1개 = 스택 512KB~1MB → 1000개면 500MB~ |
| 컨텍스트 스위칭 폭발 | CPU 코어 8개, 쓰레드 1000개 → 실제 동시 실행은 8개뿐 |
| thundering herd | IO 완료 시 수백 개 쓰레드가 동시에 깨어나 경합 |

> MAX_CAP = 32767로 상한이 있지만, IO 바운드 작업이 많다면 ManagedBlocker보다 **Virtual Thread**가 근본적인 해결책이다.

---

## 7. 자주 헷갈린 포인트

**isReleasable=false는 "블로킹 중"이 아니다**
- false = "아직 결과 없음, 블로킹 필요"
- true  = "결과 있음, 블로킹 불필요"
- 쓰레드 상태와 무관한 사전 체크값

**ManagedBlocker는 쓰레드가 아니다**
- 블로킹 작업을 캡슐화한 객체
- ForkJoinPool에 신호를 보내는 건 `managedBlock()` 호출 자체

**join()은 단순 대기가 아니다**
- 결과를 기다리는 동안 내부적으로 work-stealing 수행
- 하지만 compute()보다는 항상 오버헤드가 있음

**보상 쓰레드는 IO 완료 즉시 제거되지 않는다**
- 원래 워커와 잠깐 공존하며 태스크 처리
- keepAlive 이후 제거