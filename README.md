# Deep Dive with AI

> AI(Claude)와 페어 학습하며, 핵심 원리를 **코드로 직접 구현**하고 검증한 기록.

## Projects

| 프로젝트 | 주제 | 핵심 키워드 |
|----------|------|------------|
| [java-async](java-async) | Java 비동기 처리 흐름 | Thread, ExecutorService, Future, CompletableFuture, @Async |
| [java-forkjoinpool](java-forkjoinpool) | ForkJoinPool 내부 구현 | Work-Stealing, RecursiveTask/Action, ManagedBlocker |
| [java-messagebroker](java-messagebroker) | Kafka 핵심 원리 구현 | CommitLog, Partition, Producer/Consumer, ConsumerGroup |
| [java-threadpool](java-threadpool) | ThreadPool 동작 원리 | Work-Stealing Deque, WorkerThread, Pool Monitoring |
| [java-virtualthread](java-virtualthread) | Virtual Thread 동작과 함정 | Continuation, Mounting, Pinning, ScopedValue |
| [spring-stomp-websocket](spring-stomp-websocket) | Spring WebSocket/STOMP | Raw WebSocket, STOMP, 인증, 세션 관리, 스케일링 |

## How to Run

각 프로젝트는 독립 Gradle 프로젝트입니다.

```bash
cd <project-name>
./gradlew test
```

## About

이 레포지토리는 AI(Claude Code)와의 페어 프로그래밍을 통해 학습한 프로젝트 모음입니다.

각 프로젝트는 다음 흐름으로 진행됩니다:
1. 커리큘럼 설계 (CURRICULUM.md)
2. 핵심 개념을 코드로 직접 구현
3. 테스트로 동작 검증
4. 복기 및 리뷰 (REVIEW*.md)
