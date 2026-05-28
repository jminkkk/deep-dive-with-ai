# Spring STOMP WebSocket from Scratch

> Raw WebSocket의 고통에서 출발하여, STOMP가 **왜 필요한지**를 직접 체험하며 배우는 교육용 프로젝트.

## 무엇을 배울 수 있나

| Stage | 주제 | 핵심 코드 | 테스트 |
|-------|------|---------|-------|
| 0. Raw WebSocket의 고통 | [RawChatHandler.java](src/main/java/com/learning/websocket/stage0/RawChatHandler.java) | 5개 |
| 1. STOMP 도입 | [StompConfig.java](src/main/java/com/learning/websocket/stage1/StompConfig.java), [GreetingController.java](src/main/java/com/learning/websocket/stage1/GreetingController.java) | 5개 |
| 2. 메시지 라우팅 심화 | [ChatController.java](src/main/java/com/learning/websocket/stage2/ChatController.java) | 8개 |
| 3. 인증/인가 | [AuthChannelInterceptor.java](src/main/java/com/learning/websocket/stage3/AuthChannelInterceptor.java) | 7개 |
| 4. 세션 관리 & 에러 처리 & 서버 Push | [SessionTracker.java](src/main/java/com/learning/websocket/stage4/SessionTracker.java), [PushController.java](src/main/java/com/learning/websocket/stage4/PushController.java) | 8개 |
| 5. 스케일링 & 성능 | [ScalableStompConfig.java](src/main/java/com/learning/websocket/stage5/ScalableStompConfig.java) | 5개 |

## 30초 체험

```bash
cd spring-stomp-websocket
./gradlew test
```

테스트가 통과하면 Stage 0부터 순서대로 코드를 읽어라. 각 Stage가 이전 Stage의 한계를 어떻게 해결하는지 따라가는 것이 핵심이다.

## 학습 시작하기

**[📖 CURRICULUM.md](CURRICULUM.md)를 따라가는 것을 권장한다.**

Problem-first evolution 패턴으로 구성되어 있다. 각 Stage는 독립적으로 실행 가능하며, "불편함 체험 → 개선 → 새로운 한계 발견 → 다음 Stage"의 흐름을 따른다.

## 구현 범위와 한계

| 항목 | 상태 | 비고 |
|------|------|------|
| Raw WebSocket 핸들러 | ✅ | 수동 파싱/라우팅/브로드캐스트의 문제점 체험 |
| STOMP 기본 설정 (@MessageMapping, @SendTo) | ✅ | SimpleBroker 기반 |
| 메시지 라우팅 (@SendToUser, @SubscribeMapping, @DestinationVariable) | ✅ | MessageConverter 함정 포함 |
| STOMP 레벨 인증/인가 | ✅ | ChannelInterceptor 기반 (Spring Security 연동 아님) |
| 세션 생명주기 이벤트 | ✅ | SessionConnectEvent ~ SessionDisconnectEvent |
| 전역 예외 처리 | ✅ | @MessageExceptionHandler + @ControllerAdvice |
| SimpMessagingTemplate (서버 Push) | ✅ | REST → WebSocket push |
| Heartbeat 설정 | ✅ | 좀비 세션 방지 |
| 스레드 풀 / 전송 계층 성능 설정 | ✅ | 참고 코드 + 설명 |
| 외부 브로커 (RabbitMQ/ActiveMQ) 연동 | ⚠️ | 참고 코드만 제공, 실제 연동 없음 |
| SockJS fallback | ❌ | 스코프 외 |
| 프론트엔드 클라이언트 | ❌ | 테스트 코드로 대체 |

## 참고

- [Spring WebSocket Reference](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [STOMP Protocol Specification 1.2](https://stomp.github.io/stomp-specification-1.2.html)
- [Spring WebSocket Performance Configuration](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html)
