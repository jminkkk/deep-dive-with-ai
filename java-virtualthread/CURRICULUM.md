# Java Virtual Thread 학습 커리큘럼

## 학습 목표

Virtual Thread의 **동작 원리**와 **함정**을 코드로 직접 실행하며 이해한다.
- 왜 Virtual Thread가 필요한가 (플랫폼 스레드 한계 체험)
- Virtual Thread가 어떻게 동시성을 높이는가 (unmounting 동작 관찰)
- 어디서 함정에 빠지는가 (pinning, ThreadLocal 함정 직접 확인)

## 선수 지식

- Java 스레드 기초 (Thread, Runnable, ExecutorService)
- java-threadpool 프로젝트 학습 완료 권장 (ForkJoinPool, Work-Stealing 이해)

## 학습 패턴

이 프로젝트는 **Hybrid** 방식을 사용한다:
- Chapter 1: **Problem-first** — 플랫폼 스레드의 불편함을 먼저 체험
- Chapter 2~5: **Observe & Build** — 동작을 관찰하고, 핵심 메커니즘을 직접 구현

## 목차

- [1. 플랫폼 스레드의 한계](#1-플랫폼-스레드의-한계)
- [2. Virtual Thread 기초 API](#2-virtual-thread-기초-api)
- [3. Mounting & Continuation](#3-mounting--continuation)
- [4. Pinning 함정과 해결](#4-pinning-함정과-해결)
- [5. 실전 패턴](#5-실전-패턴)

---

# Part I: Virtual Thread 동작 원리

## 1. 플랫폼 스레드의 한계

> I/O 바운드 작업에서 플랫폼 스레드 풀이 왜 병목이 되는지 직접 확인한다.

🧪 **관련 테스트**: [`PlatformVsVirtualThreadTest`](src/test/java/com/jiminkkk/virtualthread/chapter1/PlatformVsVirtualThreadTest.java) (3개 테스트)

### 1-1. 플랫폼 스레드는 I/O 대기 중 OS 리소스를 점유한다

플랫폼 스레드는 OS 스레드와 1:1 대응한다. I/O 대기 중 스레드가 블로킹되면
그 스레드는 아무 일도 하지 않으면서 ~1MB 스택을 점유한다.
스레드 풀의 모든 스레드가 블로킹되면 새 요청이 대기 큐에 쌓인다.

📄 [`PlatformThreadBound.java:40`](src/main/java/com/jiminkkk/virtualthread/chapter1/PlatformThreadBound.java#L40) — `simulateIO()` 호출 시 스레드가 블로킹되는 지점

🧪 [`PlatformVsVirtualThreadTest.java:18`](src/test/java/com/jiminkkk/virtualthread/chapter1/PlatformVsVirtualThreadTest.java#L18) — 1000개 작업, 풀 크기 200 → 5배치 × 20ms = 100ms+ 소요 확인

> **자기 점검**: 풀 크기를 2000으로 늘리면 같은 시간에 처리되는가? 그렇다면 왜 풀을 무한정 키우지 않는가?

### 1-2. Virtual Thread는 I/O 대기 중 carrier thread를 반납한다

Virtual Thread는 I/O 블로킹 시 carrier thread에서 **언마운트(unmount)**된다.
빈 carrier thread가 즉시 다른 Virtual Thread를 실행한다.
덕분에 소수의 carrier thread로 수십만 VT의 동시 I/O 처리가 가능하다.

📄 [`VirtualThreadBound.java:39`](src/main/java/com/jiminkkk/virtualthread/chapter1/VirtualThreadBound.java#L39) — `simulateIO()` 호출 → 내부적으로 VT가 carrier에서 unmount됨

🧪 [`PlatformVsVirtualThreadTest.java:34`](src/test/java/com/jiminkkk/virtualthread/chapter1/PlatformVsVirtualThreadTest.java#L34) — 1000개 작업 → 20ms + 오버헤드로 완료

> **자기 점검**: 1000개 VT가 동시에 sleep한다. 실제로 몇 개의 OS 스레드가 사용되는가? (힌트: CPU 코어 수를 확인해라)

---

## 2. Virtual Thread 기초 API

> Virtual Thread를 만드는 5가지 방법과 플랫폼 스레드와의 특성 차이를 코드로 확인한다.

🧪 **관련 테스트**: [`VirtualThreadBasicsTest`](src/test/java/com/jiminkkk/virtualthread/chapter2/VirtualThreadBasicsTest.java) (8개 테스트)

### 2-1. 생성 방법 5가지

JEP 444에서 Virtual Thread API는 `Thread` 클래스에 통합되었다.
`Thread.ofVirtual()`은 플랫폼 스레드의 `Thread.ofPlatform()`과 대칭 구조를 가진다.

📄 [`VirtualThreadCreation.java:17`](src/main/java/com/jiminkkk/virtualthread/chapter2/VirtualThreadCreation.java#L17) — 5가지 생성 방법 (builder, convenience, unstarted, factory, executor)

🧪 [`VirtualThreadBasicsTest.java:83`](src/test/java/com/jiminkkk/virtualthread/chapter2/VirtualThreadBasicsTest.java#L83) — 이름 자동 증가 확인 (worker-0, worker-1, worker-2)

> **자기 점검**: `Executors.newVirtualThreadPerTaskExecutor()`와 `Executors.newCachedThreadPool()` 의 차이는 무엇인가?

### 2-2. 고정된 특성 (플랫폼 스레드와 다른 점)

| 특성 | 플랫폼 스레드 | Virtual Thread |
|------|------------|--------------|
| `isVirtual()` | false | **true** |
| `isDaemon()` | 설정 가능 | **항상 true** |
| `getPriority()` | 1~10 설정 가능 | **항상 5 (NORM_PRIORITY)** |

📄 [`ThreadCharacteristics.java:23`](src/main/java/com/jiminkkk/virtualthread/chapter2/ThreadCharacteristics.java#L23) — 특성 비교 출력

🧪 [`VirtualThreadBasicsTest.java:42`](src/test/java/com/jiminkkk/virtualthread/chapter2/VirtualThreadBasicsTest.java#L42) — daemon=false 설정 시 IllegalArgumentException 확인

> **자기 점검**: daemon=true가 고정된 이유는 무엇인가? 만약 VT가 non-daemon이라면 어떤 문제가 발생할 수 있는가?

---

## 3. Mounting & Continuation

> carrier thread 수보다 많은 VT가 어떻게 동시에 실행될 수 있는지, park/unpark 메커니즘을 직접 관찰한다.

🧪 **관련 테스트**: [`MountingBehaviorTest`](src/test/java/com/jiminkkk/virtualthread/chapter3/MountingBehaviorTest.java) (4개 테스트)

### 3-1. Unmounting: carrier 수보다 많은 VT 동시 실행

Carrier thread는 기본적으로 CPU 코어 수와 같다 (보통 4~16개).
VT가 `Thread.sleep()`, socket I/O 등 blocking 호출을 만나면 carrier에서 **언마운트**된다.
빈 carrier는 즉시 다른 VT를 **마운트**한다.

📄 [`MountingObserver.java:36`](src/main/java/com/jiminkkk/virtualthread/chapter3/MountingObserver.java#L36) — blocking 전후 VT 상태 기록

🧪 [`MountingBehaviorTest.java:24`](src/test/java/com/jiminkkk/virtualthread/chapter3/MountingBehaviorTest.java#L24) — carrier 8개로 VT 80개를 50ms에 처리 (unmounting 없이는 8×50ms=400ms 필요)

> **자기 점검**: 테스트 출력에서 "carrier 8개, VT 80개 → 54ms"를 확인했다. 만약 unmounting이 없다면 몇 ms가 걸렸을 것인가?

### 3-2. VT 재개 후 동일성

blocking 전후에 `Thread.currentThread()`는 **같은 VT 객체**를 반환한다.
내부적으로 다른 carrier thread가 실행하더라도 VT의 정체성(참조, 이름, ThreadLocal)은 유지된다.

📄 [`MountingObserver.java:45`](src/main/java/com/jiminkkk/virtualthread/chapter3/MountingObserver.java#L45) — `sameRef=true` 확인 — 재개 후에도 같은 VT 객체

🧪 [`MountingBehaviorTest.java:60`](src/test/java/com/jiminkkk/virtualthread/chapter3/MountingBehaviorTest.java#L60) — `before == after` 가 true임을 검증

> **자기 점검**: VT 재개 후 carrier thread가 달라질 수 있다. 그렇다면 ThreadLocal 값은 어떻게 유지되는가?

### 3-3. Continuation: park/unpark로 직접 체험

VT가 blocking 호출을 만나면 내부적으로 `LockSupport.park()`가 호출되고 carrier를 반납한다.
I/O 완료 후 JDK 스케줄러가 `LockSupport.unpark(vt)`를 호출해 중단된 지점부터 재개된다.

📄 [`ContinuationConcept.java:45`](src/main/java/com/jiminkkk/virtualthread/chapter3/ContinuationConcept.java#L45) — `LockSupport.park()`로 중단, `unpark()`로 재개 시뮬레이션

⚠️ **실제와의 차이**: 이 클래스는 교육용 시뮬레이션이다. 실제 VT의 continuation은
`jdk.internal.vm.Continuation` (내부 API)으로 처리되며 개발자가 직접 다루지 않는다.
실제 코드는 openjdk: `java.lang.VirtualThread#park()` 를 확인하라.

🧪 [`MountingBehaviorTest.java:83`](src/test/java/com/jiminkkk/virtualthread/chapter3/MountingBehaviorTest.java#L83) — park 후 resume 시 "hello + world" 결과 확인

> **자기 점검**: `LockSupport.park()`와 `Thread.sleep()`의 차이는 무엇인가? 둘 다 VT를 unmount하는가?

---

## 4. Pinning 함정과 해결

> `synchronized` 블록에서 blocking 시 carrier가 고정(pinning)되는 현상을 성능 차이로 관찰하고, `ReentrantLock`으로 해결한다.

🧪 **관련 테스트**: [`PinningTest`](src/test/java/com/jiminkkk/virtualthread/chapter4/PinningTest.java) (3개 테스트)

### 4-1. Pinning 발생 원리

Java 21에서 `synchronized`는 OS 모니터(객체 헤더)를 사용한다.
모니터는 carrier thread에 묶여 있어, `synchronized` 블록 안에서 blocking이 발생해도
VT가 carrier를 반납하지 못한다 → **Pinning**.

```
// ❌ Pinning 발생 (Java 21)
synchronized (lock) {
    Thread.sleep(100);  // 이 VT가 carrier를 100ms 동안 독점
}
```

📄 [`SynchronizedPinning.java:50`](src/main/java/com/jiminkkk/virtualthread/chapter4/SynchronizedPinning.java#L50) — `synchronized` + `Thread.sleep()` → pinning 발생

🧪 [`PinningTest.java:25`](src/test/java/com/jiminkkk/virtualthread/chapter4/PinningTest.java#L25) — carrier 8개, task 32개, 100ms I/O → **421ms** (4배 지연 확인)

테스트 실행 시 `-Djdk.tracePinnedThreads=short` 옵션 덕분에 콘솔에 pinning 스택 트레이스가 출력된다.

> **자기 점검**: Pinning이 발생해도 프로그램이 멈추지는 않는다. 그렇다면 왜 문제인가? (힌트: 동시 요청 수가 carrier 수를 초과하면 어떻게 되는가?)

### 4-2. ReentrantLock으로 해결

`ReentrantLock`은 `LockSupport.park()` 기반으로 동작한다.
blocking 시 VT가 carrier를 반납(unmount)하므로 pinning이 발생하지 않는다.

```
// ✅ Pinning 없음
lock.lock();
try {
    Thread.sleep(100);  // 이 VT가 carrier를 반납하고 park 상태로 대기
} finally {
    lock.unlock();
}
```

📄 [`ReentrantLockFix.java:49`](src/main/java/com/jiminkkk/virtualthread/chapter4/ReentrantLockFix.java#L49) — `ReentrantLock` + `Thread.sleep()` → pinning 없음

🧪 [`PinningTest.java:42`](src/test/java/com/jiminkkk/virtualthread/chapter4/PinningTest.java#L42) — 같은 조건에서 **106ms** (pinning 대비 4x 빠름)

> **자기 점검**: 기존 코드에서 `synchronized`를 `ReentrantLock`으로 바꿀 때 주의사항은 무엇인가? (힌트: try-finally)

---

## 5. 실전 패턴

> Virtual Thread 환경에서 자주 실수하는 패턴과 올바른 사용법을 코드로 확인한다.

🧪 **관련 테스트**: [`VirtualThreadPatternsTest`](src/test/java/com/jiminkkk/virtualthread/chapter5/VirtualThreadPatternsTest.java) (4개 테스트)

### 5-1. ThreadLocal 함정

플랫폼 스레드 풀에서 ThreadLocal 캐싱은 효과적이다 (스레드가 재사용되므로).
VT 환경에서 ThreadLocal 캐싱은 함정이다 — VT는 재사용되지 않아 매번 새 인스턴스가 생성된다.

📄 [`ThreadLocalTrap.java:36`](src/main/java/com/jiminkkk/virtualthread/chapter5/ThreadLocalTrap.java#L36) — ThreadLocal 캐싱 안티패턴

🧪 [`VirtualThreadPatternsTest.java:24`](src/test/java/com/jiminkkk/virtualthread/chapter5/VirtualThreadPatternsTest.java#L24) — 100개 작업 중 100번 신규 초기화 (캐싱 효과 0%)

> **자기 점검**: ThreadLocal을 VT에서 완전히 쓰면 안 되는가? 어떤 경우에는 여전히 괜찮은가?

### 5-2. Semaphore로 동시성 제한

VT는 풀링하지 않으므로 `newFixedThreadPool(10)`으로 동시성을 제한할 수 없다.
외부 리소스(DB, API QPS) 동시 접근 제한에는 `Semaphore`를 사용한다.
VT는 `semaphore.acquire()` blocking 시 carrier를 반납하므로 대기 중인 VT가 많아도 carrier가 고갈되지 않는다.

📄 [`SemaphoreLimiter.java:39`](src/main/java/com/jiminkkk/virtualthread/chapter5/SemaphoreLimiter.java#L39) — Semaphore + VT 조합

🧪 [`VirtualThreadPatternsTest.java:50`](src/test/java/com/jiminkkk/virtualthread/chapter5/VirtualThreadPatternsTest.java#L50) — 제한 3개, 작업 20개 → 최대 동시 실행 3개 확인

> **자기 점검**: Semaphore의 `acquire()` 자체가 blocking이다. VT에서 이 blocking은 pinning을 유발하는가?

### 5-3. Thread-per-request 패턴

VT 덕분에 요청마다 스레드를 생성하는 전통적인 동기 패턴을 비동기 없이 유지할 수 있다.
`CompletableFuture`, reactive 라이브러리 없이도 높은 동시성을 달성한다.

🧪 [`VirtualThreadPatternsTest.java:98`](src/test/java/com/jiminkkk/virtualthread/chapter5/VirtualThreadPatternsTest.java#L98) — 500개 동시 요청, 각 2회 I/O → 동기 코드로 처리

> **자기 점검**: Spring Boot 3.2+ 에서 `spring.threads.virtual.enabled=true` 설정이 무엇을 하는가?

---

## 참고 문서

| 문서 | 용도 |
|------|------|
| [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) | Virtual Thread 설계 스펙 (1차 출처) |
| [Thread Javadoc (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html) | API 레퍼런스 |
| [JEP 491: Synchronized pinning fix](https://openjdk.org/jeps/491) | Java 24에서 pinning 해결 |
