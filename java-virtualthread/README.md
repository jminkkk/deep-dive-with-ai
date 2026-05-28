# Java Virtual Thread from scratch

> Virtual Thread의 동작 원리와 함정을 **코드로 직접 실행**하며 이해하는 교육용 프로젝트.

## 무엇을 배울 수 있나

| 챕터 | 핵심 코드 | 테스트 결과 (실측) |
|------|---------|--------------|
| 1. 플랫폼 스레드의 한계 | [`PlatformThreadBound`](src/main/java/com/jiminkkk/virtualthread/chapter1/PlatformThreadBound.java) vs [`VirtualThreadBound`](src/main/java/com/jiminkkk/virtualthread/chapter1/VirtualThreadBound.java) | 플랫폼 133ms vs Virtual 34ms (5.6x) |
| 2. Virtual Thread 기초 API | [`VirtualThreadCreation`](src/main/java/com/jiminkkk/virtualthread/chapter2/VirtualThreadCreation.java) | daemon/priority 고정 특성 확인 |
| 3. Mounting & Continuation | [`ContinuationConcept`](src/main/java/com/jiminkkk/virtualthread/chapter3/ContinuationConcept.java) | carrier 8개로 VT 80개 → 54ms |
| 4. Pinning 함정과 해결 | [`SynchronizedPinning`](src/main/java/com/jiminkkk/virtualthread/chapter4/SynchronizedPinning.java) vs [`ReentrantLockFix`](src/main/java/com/jiminkkk/virtualthread/chapter4/ReentrantLockFix.java) | synchronized 421ms vs Lock 106ms (4x) |
| 5. 실전 패턴 | [`ThreadLocalTrap`](src/main/java/com/jiminkkk/virtualthread/chapter5/ThreadLocalTrap.java), [`SemaphoreLimiter`](src/main/java/com/jiminkkk/virtualthread/chapter5/SemaphoreLimiter.java) | 100,000 VT → 135ms |

## 30초 체험

```bash
cd java-virtualthread
./gradlew test
```

핵심 결과만 보려면:

```bash
# Chapter 1: 플랫폼 vs Virtual Thread 성능 비교
./gradlew test --tests "*.chapter1.*"

# Chapter 4: Pinning 발생 및 콘솔에 스택 트레이스 출력
./gradlew test --tests "*.chapter4.*"
```

## 학습 시작하기

**[📖 CURRICULUM.md](CURRICULUM.md)를 따라가는 것을 권장합니다.**

순서: Chapter 1 → 2 → 3 → 4 → 5

## java-threadpool과의 관계

이 프로젝트는 `java-threadpool` 학습 이후를 전제로 한다.

| 프로젝트 | 핵심 | 선수 지식 |
|---------|------|---------|
| java-threadpool | ThreadPoolExecutor, ForkJoinPool, Work-Stealing | Java 스레드 기초 |
| java-virtualthread | Virtual Thread, Mounting/Unmounting, Pinning | java-threadpool 권장 |

Virtual Thread의 carrier thread는 ForkJoinPool 기반이다. java-threadpool에서 ForkJoinPool을 공부했다면 carrier thread 동작을 더 잘 이해할 수 있다.

## 구현 범위와 한계

| 항목 | 상태 | 비고 |
|------|------|------|
| Virtual Thread API 기초 | ✅ | JEP 444 기준 |
| Unmounting 동작 관찰 | ✅ | 간접 관찰 (성능 측정) |
| Continuation 직접 체험 | ⚠️ | LockSupport로 시뮬레이션 — 실제 `jdk.internal.vm.Continuation`은 내부 API |
| Pinning 발생/해결 | ✅ | Java 21 기준 (Java 24에서 JEP 491로 해결) |
| Carrier thread 직접 조회 | ❌ | 공개 API 없음 — JFR로만 확인 가능 |
| ScopedValue | ❌ | Java 21 Preview — 별도 학습 권장 |

## 참고

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Thread Javadoc (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html)
- [JEP 491: Synchronized Pinning Fix (Java 24)](https://openjdk.org/jeps/491)
