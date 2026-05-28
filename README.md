# Deep Dive with AI

> AI(Claude Code)와 페어 학습하며, 핵심 원리를 **코드로 직접 구현**하고 검증한 기록.

## What is this?

블로그나 강의로 배우는 대신, **동작하는 코드와 테스트**로 기술의 내부 원리를 학습하는 프로젝트 모음입니다.

각 프로젝트는 다음 흐름으로 진행됩니다:
1. 공식 문서/스펙 기반으로 커리큘럼 설계 (`CURRICULUM.md`)
2. 핵심 개념을 코드로 직접 구현
3. 테스트로 동작 검증
4. 복기 및 리뷰 (`REVIEW*.md`)

모든 과정은 AI(Claude Code)와의 페어 프로그래밍으로 진행되며, 커밋 히스토리에 그 흔적이 남아 있습니다.

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

## How to Add a New Project

새 학습 주제를 추가할 때의 작업 방식입니다.

### 1. 프로젝트 생성

Claude Code에서 `/learning-project-generator` 스킬을 사용하면 이 레포 하위에 자동 생성됩니다.

```
/learning-project-generator
> "Redis 내부 구조를 배우고 싶어"
```

### 2. 프로젝트 구조

생성되는 프로젝트는 아래 구조를 따릅니다:

```
{topic}/
├── README.md          # 프로젝트 소개 + 30초 체험
├── CURRICULUM.md      # 학습 커리큘럼 (핵심)
├── build.gradle
└── src/
    ├── main/java/     # 핵심 구현체
    └── test/java/     # 챕터별 테스트
```

### 3. 학습 완료 후

- `REVIEW*.md` 작성 (복기용, `/review-sheet` 스킬 사용)
- 이 README의 Projects 테이블에 새 행 추가
- 커밋 & push
