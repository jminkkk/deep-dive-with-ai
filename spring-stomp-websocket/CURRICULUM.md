# Spring STOMP WebSocket 학습 커리큘럼

## 학습 목표
Spring WebSocket + STOMP의 동작 원리를 **코드로 직접 실행하며** 이해한다.
Raw WebSocket의 문제점에서 출발하여, STOMP가 왜 필요한지를 체험하고,
메시지 라우팅 → 인증/인가 → 세션 관리 → 스케일링까지 단계적으로 학습한다.

## 학습 패턴
**Problem-first evolution** — "왜 이렇게 됐는가"를 체험하는 구조.
각 Stage는 이전 Stage의 한계를 해결하며, 독립적으로 실행 가능하다.

## 선수 지식
- Java 17+, Spring Boot 기본 사용
- HTTP 요청/응답 모델 이해
- WebSocket이 "양방향 통신"이라는 정도의 개념

## 실행 방법
```bash
./gradlew test
```

## 목차

| Stage | 주제 | 테스트 | 핵심 질문 |
|-------|------|--------|----------|
| 0 | Raw WebSocket의 고통 | 5개 | STOMP 없이 채팅을 만들면 뭐가 불편한가? |
| 1 | STOMP 도입 — 자동화된 메시징 | 5개 | STOMP가 Raw WebSocket의 어떤 문제를 해결하는가? |
| 2 | 메시지 라우팅 심화 | 8개 | @SendTo vs @SendToUser vs @SubscribeMapping 차이는? |
| 3 | 인증/인가 | 7개 | STOMP 메시지 레벨에서 보안을 어떻게 적용하는가? |
| 4 | 세션 관리 & 에러 처리 & 서버 Push | 8개 | 운영 환경에서 필요한 세션/에러/push 처리는? |
| 5 | 스케일링 & 성능 | 5개 | SimpleBroker의 한계와 프로덕션 설정은? |

**총 38개 테스트**

---

# Stage 0: Raw WebSocket의 고통

> STOMP 없이 순수 WebSocket만으로 채팅을 구현하며, 직접 부딪히는 문제들을 체험한다.

- ✅ WebSocket 양방향 통신의 기본 동작 이해
- ⚠️ 한계: 메시지 포맷, 라우팅, 브로드캐스트, 에러 처리를 모두 직접 구현해야 한다

🧪 **테스트**: [`Stage0_RawWebSocketTest`](src/test/java/com/learning/websocket/stage0/Stage0_RawWebSocketTest.java) (5개)

### 0-1. 수동 JSON 파싱

메시지 포맷이 없으므로 클라이언트-서버 간 JSON 형식을 "약속"하고 직접 파싱해야 한다.
약속이 깨지면 런타임에서야 에러를 발견한다.

📄 [`RawChatHandler.java:42`](src/main/java/com/learning/websocket/stage0/RawChatHandler.java#L42) — 수동 JSON 파싱 + 에러 처리

🧪 [`Stage0_RawWebSocketTest.java:80`](src/test/java/com/learning/websocket/stage0/Stage0_RawWebSocketTest.java#L80) — 올바른 JSON 전송

🧪 [`Stage0_RawWebSocketTest.java:100`](src/test/java/com/learning/websocket/stage0/Stage0_RawWebSocketTest.java#L100) — 잘못된 JSON 전송 시 에러 응답

### 0-2. 수동 라우팅 (switch/case)

메시지 타입별로 분기하는 로직을 직접 작성해야 한다. 타입이 늘어날수록 switch가 비대해진다.

📄 [`RawChatHandler.java:60`](src/main/java/com/learning/websocket/stage0/RawChatHandler.java#L60) — switch 기반 메시지 타입 라우팅

🧪 [`Stage0_RawWebSocketTest.java:124`](src/test/java/com/learning/websocket/stage0/Stage0_RawWebSocketTest.java#L124) — 알 수 없는 타입에 대한 에러

### 0-3. 수동 브로드캐스트 & DM

세션 목록을 직접 관리하고 순회해야 한다. 특정 사용자에게 메시지를 보내려면 세션 ID를 알아야 한다.

📄 [`RawChatHandler.java:122`](src/main/java/com/learning/websocket/stage0/RawChatHandler.java#L122) — 세션 순회 브로드캐스트

📄 [`RawChatHandler.java:87`](src/main/java/com/learning/websocket/stage0/RawChatHandler.java#L87) — 세션 ID 기반 DM

🧪 [`Stage0_RawWebSocketTest.java:152`](src/test/java/com/learning/websocket/stage0/Stage0_RawWebSocketTest.java#L152) — DM 대상 세션을 찾지 못하는 문제

🧪 [`Stage0_RawWebSocketTest.java:182`](src/test/java/com/learning/websocket/stage0/Stage0_RawWebSocketTest.java#L182) — 수동 브로드캐스트 동작 확인

> **자기 점검**: Raw WebSocket에서 "채팅방" 기능을 추가하려면 코드 어디를 수정해야 하는가? 그 변경이 기존 코드에 미치는 영향은?

---

# Stage 1: STOMP 도입 — 자동화된 메시징

> Stage 0의 수동 파싱/라우팅/브로드캐스트를 STOMP + Spring이 모두 대체한다.

- ✅ 개선: JSON 파싱, 라우팅, 브로드캐스트가 애노테이션 하나로 해결된다
- ⚠️ 한계: 모든 메시지가 모든 구독자에게 전달된다. 개인 메시지, 초기 데이터 로딩 방법이 없다

🧪 **테스트**: [`Stage1_StompBasicTest`](src/test/java/com/learning/websocket/stage1/Stage1_StompBasicTest.java) (5개)

### 1-1. STOMP 설정의 핵심 3요소

```
/app   — 클라이언트 SEND 시 @MessageMapping으로 라우팅되는 prefix
/topic — 브로커가 구독자에게 메시지를 배포하는 prefix
/ws    — WebSocket 핸드셰이크 엔드포인트
```

📄 [`StompConfig.java:31`](src/main/java/com/learning/websocket/stage1/StompConfig.java#L31) — STOMP 브로커 설정

### 1-2. @MessageMapping + @SendTo

클라이언트가 `/app/greeting`으로 SEND → `@MessageMapping("/greeting")` 매칭 → 반환값이 `@SendTo("/topic/greetings")`로 브로드캐스트.

📄 [`GreetingController.java:41`](src/main/java/com/learning/websocket/stage1/GreetingController.java#L41) — @MessageMapping + @SendTo

🧪 [`Stage1_StompBasicTest.java:72`](src/test/java/com/learning/websocket/stage1/Stage1_StompBasicTest.java#L72) — 자동 라우팅 + 브로드캐스트

🧪 [`Stage1_StompBasicTest.java:94`](src/test/java/com/learning/websocket/stage1/Stage1_StompBasicTest.java#L94) — 다중 구독자 자동 브로드캐스트

### 1-3. Destination prefix 규칙 (함정 포함)

SEND는 `/app` prefix, SUBSCRIBE는 `/topic` 또는 `/queue` prefix. 이 규칙을 모르면 메시지가 사라진다.

🧪 [`Stage1_StompBasicTest.java:118`](src/test/java/com/learning/websocket/stage1/Stage1_StompBasicTest.java#L118) — 함정: 구독하지 않은 destination은 수신 불가

🧪 [`Stage1_StompBasicTest.java:134`](src/test/java/com/learning/websocket/stage1/Stage1_StompBasicTest.java#L134) — 함정: /app prefix 없이 보내면 @MessageMapping에 도달 불가

🧪 [`Stage1_StompBasicTest.java:153`](src/test/java/com/learning/websocket/stage1/Stage1_StompBasicTest.java#L153) — 정리: destination prefix 라우팅 규칙

> **자기 점검**: 클라이언트가 `/topic/greetings`로 SEND하면 어떻게 되는가? @MessageMapping에 도달하는가, 브로커가 직접 처리하는가?

---

# Stage 2: 메시지 라우팅 심화

> 브로드캐스트만으로는 부족하다. 개인 응답, 초기 데이터 로딩, 채팅방 분리를 학습한다.

- ✅ 개선: 1:1 응답(@SendToUser), 구독 시 초기 데이터(@SubscribeMapping), 채팅방 분리(@DestinationVariable)
- ⚠️ 한계: 누구나 연결하고 메시지를 보낼 수 있다. 인증/인가가 없다

🧪 **테스트**: [`Stage2_MessageRoutingTest`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java) (8개)

### 2-1. @SendTo — 브로드캐스트 (1:N)

모든 구독자에게 메시지를 전달한다. Stage 1에서 배운 패턴의 심화.

📄 [`ChatController.java:38`](src/main/java/com/learning/websocket/stage2/ChatController.java#L38) — broadcastMessage()

🧪 [`Stage2_MessageRoutingTest.java:62`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L62) — 두 세션 모두 수신

### 2-2. @SendToUser — 개인 응답 (1:1)

메시지를 보낸 사용자에게만 응답한다. 내부적으로 `/queue/reply` → `/user/{sessionId}/queue/reply`로 변환.

📄 [`ChatController.java:58`](src/main/java/com/learning/websocket/stage2/ChatController.java#L58) — privateReply()

🧪 [`Stage2_MessageRoutingTest.java:82`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L82) — sender만 응답 수신, 다른 세션은 null

### 2-3. @SubscribeMapping — 구독 시 초기 데이터

SEND 없이 구독 즉시 데이터를 반환한다. **brokerChannel을 거치지 않는다** — 이 차이가 핵심.

📄 [`ChatController.java:74`](src/main/java/com/learning/websocket/stage2/ChatController.java#L74) — onSubscribe() (브로커 우회)

🧪 [`Stage2_MessageRoutingTest.java:110`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L110) — 구독 즉시 초기 데이터 수신

🧪 [`Stage2_MessageRoutingTest.java:125`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L125) — 함정: 다른 구독자에게는 전달되지 않는다

> **자기 점검**: @SubscribeMapping에 @SendTo를 추가하면 동작이 어떻게 바뀌는가? 왜 기본 동작이 브로커를 우회하도록 설계되었는가?

### 2-4. @DestinationVariable — 채팅방 분리

Ant 스타일 경로 변수로 destination을 동적으로 분리한다.

📄 [`ChatController.java:102`](src/main/java/com/learning/websocket/stage2/ChatController.java#L102) — roomMessage()

🧪 [`Stage2_MessageRoutingTest.java:149`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L149) — room1 구독자만 수신, room2는 null

### 2-5. MessageConverter 함정 (silent failure)

서버 반환 타입이 `Map`/`List`/POJO면 `application/json`으로 직렬화된다.
클라이언트가 `StringMessageConverter`(text/plain 전용)를 사용하면 **에러 없이 메시지가 사라진다**.

📄 [`ChatController.java:90`](src/main/java/com/learning/websocket/stage2/ChatController.java#L90) — chatInfo(): Map 반환 (함정 시연용)

🧪 [`Stage2_MessageRoutingTest.java:172`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L172) — **함정 체험**: Map 반환 → 클라이언트 수신 불가 (null)

🧪 [`Stage2_MessageRoutingTest.java:203`](src/test/java/com/learning/websocket/stage2/Stage2_MessageRoutingTest.java#L203) — **대조**: String 반환 → 정상 수신

> **자기 점검**: 서버가 Map을 반환하는데 클라이언트에 아무것도 도착하지 않는다. 서버 로그에도 에러가 없다. 원인은 무엇이고, 해결법 두 가지는?

---

# Stage 3: 인증/인가

> Stage 0~2에서는 누구나 연결하고 메시지를 보낼 수 있었다. STOMP 메시지 레벨에서 보안을 적용한다.

- ✅ 개선: CONNECT 시 토큰 검증, destination 레벨 인가, Principal 자동 주입
- ⚠️ 한계: 연결 해제 감지, 예외 처리, heartbeat 등 운영 관점의 기능이 없다

🧪 **테스트**: [`Stage3_AuthenticationTest`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java) (7개)

### 3-1. ChannelInterceptor — STOMP CONNECT 시 인증

`clientInboundChannel`에 인터셉터를 등록하여 STOMP 프레임이 처리되기 전에 보안 검사를 수행한다.

📄 [`AuthChannelInterceptor.java:48`](src/main/java/com/learning/websocket/stage3/AuthChannelInterceptor.java#L48) — preSend(): CONNECT/SEND/SUBSCRIBE 분기

📄 [`AuthChannelInterceptor.java:75`](src/main/java/com/learning/websocket/stage3/AuthChannelInterceptor.java#L75) — handleConnect(): 토큰 검증 + Principal 설정

📄 [`SecureStompConfig.java:55`](src/main/java/com/learning/websocket/stage3/SecureStompConfig.java#L55) — configureClientInboundChannel()

🧪 [`Stage3_AuthenticationTest.java:69`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L69) — 유효한 토큰으로 연결 성공

🧪 [`Stage3_AuthenticationTest.java:80`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L80) — 토큰 없이 연결 시 거부 (ExecutionException)

🧪 [`Stage3_AuthenticationTest.java:90`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L90) — 잘못된 토큰으로 연결 시 거부

### 3-2. Destination 레벨 인가

인증된 사용자가 특정 destination에 접근할 권한이 있는지 확인한다.

📄 [`AuthChannelInterceptor.java:99`](src/main/java/com/learning/websocket/stage3/AuthChannelInterceptor.java#L99) — handleAuthorization(): destination 권한 검사

🧪 [`Stage3_AuthenticationTest.java:98`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L98) — 일반 사용자의 공개 채널 접근

🧪 [`Stage3_AuthenticationTest.java:116`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L116) — admin만 관리자 채널 접근 가능

### 3-3. Principal 주입 & STOMP login 헤더 함정

ChannelInterceptor에서 설정한 Principal이 @MessageMapping 메서드에 자동 주입된다.
STOMP 스펙의 `login`/`passcode` 헤더는 Spring에서 무시된다.

📄 [`SecureChatController.java:23`](src/main/java/com/learning/websocket/stage3/SecureChatController.java#L23) — Principal 파라미터 사용

🧪 [`Stage3_AuthenticationTest.java:136`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L136) — Principal.getName()이 인증된 사용자명 반환

🧪 [`Stage3_AuthenticationTest.java:155`](src/test/java/com/learning/websocket/stage3/Stage3_AuthenticationTest.java#L155) — 함정: STOMP login/passcode만 보내면 인증 실패

> **자기 점검**: ChannelInterceptor에서 예외를 던지면 무슨 일이 발생하는가? @MessageMapping 내부에서 예외를 던지는 것과 어떻게 다른가?

---

# Stage 4: 세션 관리 & 에러 처리 & 서버 Push

> 운영 환경에서 필수적인 세션 추적, 예외 처리, 서버 → 클라이언트 push를 학습한다.

- ✅ 개선: 세션 이벤트 기반 추적, 전역 예외 핸들링, REST → WebSocket push, heartbeat
- ⚠️ 한계: 단일 인스턴스에서만 동작한다. 수평 확장, 메시지 영속성이 없다

🧪 **테스트**: [`Stage4_SessionAndErrorTest`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java) (8개)

### 4-1. 세션 생명주기 이벤트

Spring은 STOMP 세션의 각 단계에서 ApplicationEvent를 발행한다.

```
SessionConnectEvent → SessionConnectedEvent → SessionSubscribeEvent → SessionDisconnectEvent
```

📄 [`SessionTracker.java:46`](src/main/java/com/learning/websocket/stage4/SessionTracker.java#L46) — @EventListener로 이벤트 감지

📄 [`SessionTracker.java:82`](src/main/java/com/learning/websocket/stage4/SessionTracker.java#L82) — handleDisconnect(): 멱등 연산 (같은 세션에 여러 번 호출 가능)

🧪 [`Stage4_SessionAndErrorTest.java:67`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L67) — 연결 시 세션 등록

🧪 [`Stage4_SessionAndErrorTest.java:82`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L82) — 해제 시 세션 제거

🧪 [`Stage4_SessionAndErrorTest.java:101`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L101) — 여러 세션 독립 추적

> **자기 점검**: SessionDisconnectEvent가 같은 세션에 대해 2번 발생할 수 있다. 왜 그런가? `if (contains) then remove` 대신 `remove()`를 바로 호출하는 이유는?

### 4-2. Heartbeat — 좀비 세션 방지

heartbeat가 없으면 네트워크 끊김을 감지하지 못하고 좀비 세션이 쌓인다.

📄 [`SessionConfig.java:34`](src/main/java/com/learning/websocket/stage4/SessionConfig.java#L34) — heartbeat 설정 + TaskScheduler

> **자기 점검**: heartbeat를 설정했는데 TaskScheduler를 빼면 무슨 일이 발생하는가?

### 4-3. @MessageExceptionHandler — 전역 예외 처리

`@ControllerAdvice` + `@MessageExceptionHandler`로 모든 @MessageMapping 예외를 한 곳에서 처리한다.

📄 [`GlobalStompExceptionHandler.java:26`](src/main/java/com/learning/websocket/stage4/GlobalStompExceptionHandler.java#L26) — 전역 예외 핸들러

📄 [`NotificationController.java:23`](src/main/java/com/learning/websocket/stage4/NotificationController.java#L23) — 의도적 예외 발생 메서드

🧪 [`Stage4_SessionAndErrorTest.java:123`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L123) — 예외 발생 → /user/queue/errors로 전달

🧪 [`Stage4_SessionAndErrorTest.java:143`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L143) — 정상 메시지는 예외 없이 처리

🧪 [`Stage4_SessionAndErrorTest.java:160`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L160) — 에러는 발생시킨 세션에만 전달 (broadcast=false)

### 4-4. SimpMessagingTemplate — 서버가 먼저 메시지를 보낸다

`@MessageMapping`은 클라이언트가 먼저 메시지를 보내야 동작한다.
`SimpMessagingTemplate`은 서버가 원하는 시점에 어디서든 WebSocket 메시지를 push할 수 있다.

```
[@MessageMapping]         Client SEND → Handler → @SendTo → Broker → Subscribers
[SimpMessagingTemplate]   이벤트 발생  → convertAndSend() → Broker → Subscribers
```

📄 [`PushController.java:37`](src/main/java/com/learning/websocket/stage4/PushController.java#L37) — REST → SimpMessagingTemplate → WebSocket

🧪 [`Stage4_SessionAndErrorTest.java:187`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L187) — HTTP POST → WebSocket 구독자 수신

🧪 [`Stage4_SessionAndErrorTest.java:216`](src/test/java/com/learning/websocket/stage4/Stage4_SessionAndErrorTest.java#L216) — @SendTo와 SimpMessagingTemplate이 같은 브로커를 공유하는 증명

> **자기 점검**: SimpMessagingTemplate으로 Map을 보내면 StringMessageConverter 클라이언트가 수신할 수 있는가? (Stage 2 함정 참조)

---

# Stage 5: 스케일링 & 성능

> SimpleBroker의 한계를 이해하고, 프로덕션을 위한 성능 설정과 외부 브로커 전환을 학습한다.

- ✅ 개선: 전송 계층 성능 설정, 스레드 풀 이해, 외부 브로커 아키텍처
- ⚠️ 한계: 실제 외부 브로커(RabbitMQ) 연동은 포함하지 않음 (참고 코드만 제공)

🧪 **테스트**: [`Stage5_ScalingTest`](src/test/java/com/learning/websocket/stage5/Stage5_ScalingTest.java) (5개)

### 5-1. SimpleBroker의 한계

단일 JVM 메모리 기반. 서버 재시작 시 구독 정보 소실, 수평 확장 불가, 메시지 영속성 없음.

🧪 [`Stage5_ScalingTest.java:69`](src/test/java/com/learning/websocket/stage5/Stage5_ScalingTest.java#L69) — 단일 인스턴스에서는 정상 동작

🧪 [`Stage5_ScalingTest.java:86`](src/test/java/com/learning/websocket/stage5/Stage5_ScalingTest.java#L86) — 다중 클라이언트 동시 메시징

🧪 [`Stage5_ScalingTest.java:114`](src/test/java/com/learning/websocket/stage5/Stage5_ScalingTest.java#L114) — I/O 바운드 작업 시 메시지 유실 없음 확인

### 5-2. WebSocket 전송 계층 성능 설정

느린 클라이언트가 서버를 블로킹하거나 메모리를 고갈시키는 것을 방지한다.

📄 [`ScalableStompConfig.java:73`](src/main/java/com/learning/websocket/stage5/ScalableStompConfig.java#L73) — sendTimeLimit, sendBufferSizeLimit, messageSizeLimit

🧪 [`Stage5_ScalingTest.java:140`](src/test/java/com/learning/websocket/stage5/Stage5_ScalingTest.java#L140) — 큰 메시지 정상 처리 확인

🧪 [`Stage5_ScalingTest.java:162`](src/test/java/com/learning/websocket/stage5/Stage5_ScalingTest.java#L162) — 빠른 연결/해제 반복 시 리소스 누수 없음

### 5-3. 스레드 풀 설정 — ThreadPoolExecutor의 함정

`corePoolSize=10, maxPoolSize=20, queueCapacity=Integer.MAX_VALUE` 설정 시
maxPoolSize는 **절대 효과를 발휘하지 않는다**. 큐가 가득 찬 후에만 스레드가 추가되기 때문.

📄 [`ThreadPoolConfigExample.java:43`](src/main/java/com/learning/websocket/stage5/ThreadPoolConfigExample.java#L43) — 함정 설명 + 설정 예시 (참고용)

### 5-4. 외부 브로커 전환 — RabbitMQ/ActiveMQ

`enableSimpleBroker()` → `enableStompBrokerRelay()`로 전환. `/topic`과 `/queue`의 라우팅 방식이 실제로 달라진다.

📄 [`ExternalBrokerConfigExample.java:34`](src/main/java/com/learning/websocket/stage5/ExternalBrokerConfigExample.java#L34) — 외부 브로커 설정 참고 코드

> **자기 점검**: SimpleBroker에서 /topic과 /queue는 기능 차이가 없다. RabbitMQ로 전환하면 어떤 차이가 생기는가?

---

# 참고 자료

- [Spring WebSocket Reference](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [STOMP Protocol Specification 1.2](https://stomp.github.io/stomp-specification-1.2.html)
- [Spring WebSocket Performance Configuration](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html)