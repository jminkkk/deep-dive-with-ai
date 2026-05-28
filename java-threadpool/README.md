# ForkJoinPool from Scratch

> ForkJoinPool의 핵심 원리를 **코드로 직접 구현**하며 이해하는 교육용 프로젝트.

## 무엇을 배울 수 있나

| 챕터 | 핵심 코드 | 테스트 |
|------|---------|-------|
| 1. Work-Stealing 알고리즘 | [WorkStealingDeque.java](src/main/java/com/jiminkkk/forkjoin/core/WorkStealingDeque.java) | 7개 |
| 2. 워커 스레드 구조 | [WorkerThread.java](src/main/java/com/jiminkkk/forkjoin/core/WorkerThread.java) | 2개 |
| 3. RecursiveTask 패턴 | [SumTask](src/main/java/com/jiminkkk/forkjoin/chapter3/SumTask.java), [MaxTask](src/main/java/com/jiminkkk/forkjoin/chapter3/MaxTask.java), [FibonacciTask](src/main/java/com/jiminkkk/forkjoin/chapter3/FibonacciTask.java) | 11개 |
| 4. RecursiveAction 패턴 | [MergeSortAction](src/main/java/com/jiminkkk/forkjoin/chapter4/MergeSortAction.java), [ArrayInitAction](src/main/java/com/jiminkkk/forkjoin/chapter4/ArrayInitAction.java) | 9개 |
| 5. 풀 모니터링 | [PoolMonitor.java](src/main/java/com/jiminkkk/forkjoin/chapter5/PoolMonitor.java) | 6개 |
| 6. ManagedBlocker | [SimulatedIOBlocker.java](src/main/java/com/jiminkkk/forkjoin/chapter6/SimulatedIOBlocker.java) | 6개 |

## 30초 체험

```bash
git clone <repo>
cd java-threadpool
./gradlew test
```

테스트가 통과하면 각 파일을 열어서 CURRICULUM.md의 순서대로 읽어라.

## 학습 시작하기

**[📖 CURRICULUM.md](CURRICULUM.md)를 따라가는 것을 권장한다.**

각 챕터는 "개념 설명 → 구현 코드 → 테스트 → 자기 점검"의 흐름으로 구성되어 있다.

## 구현 범위와 한계

| 항목 | 상태 | 비고 |
|------|------|------|
| Work-Stealing Deque 원리 | ✅ | synchronized 단순화 (실제는 CAS 기반 lock-free) |
| RecursiveTask (결과 반환) | ✅ | Java 표준 RecursiveTask 직접 사용 |
| RecursiveAction (void) | ✅ | Java 표준 RecursiveAction 직접 사용 |
| 풀 모니터링 API | ✅ | 스냅샷 레코드로 캡처 |
| ManagedBlocker 프로토콜 | ✅ | I/O 지연 시뮬레이션으로 구현 |
| Lock-free WorkQueue | ❌ | CAS 기반 실제 구현은 제외 (복잡도 과도) |
| CountedCompleter | ❌ | 스코프 외 |

## 참고

- [ForkJoinPool Javadoc (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html)
- [ForkJoinTask Javadoc (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinTask.html)
- [ForkJoinPool.ManagedBlocker Javadoc (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.ManagedBlocker.html)
- Doug Lea, "A Java Fork/Join Framework" (2000)
