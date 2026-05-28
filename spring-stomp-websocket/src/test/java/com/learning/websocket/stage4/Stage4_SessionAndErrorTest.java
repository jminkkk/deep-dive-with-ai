package com.learning.websocket.stage4;

import com.learning.websocket.support.StompTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stage 4: 세션 관리 & 에러 처리.
 *
 * <p>이전 Stage에서 다루지 않은 운영 관점의 문제들:
 * <ul>
 *   <li>클라이언트 연결/해제 감지</li>
 *   <li>@MessageMapping 내부 예외 처리</li>
 *   <li>heartbeat를 통한 좀비 세션 방지</li>
 *   <li>SessionDisconnectEvent의 멱등성</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Stage4_SessionAndErrorTest.TestApp.class
)
class Stage4_SessionAndErrorTest {

    @SpringBootApplication(
            scanBasePackages = "com.learning.websocket.stage4",
            exclude = SecurityAutoConfiguration.class
    )
    static class TestApp {}

    @LocalServerPort
    private int port;

    @Autowired
    private SessionTracker sessionTracker;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = StompTestSupport.createStringClient();
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) stompClient.stop();
    }

    @Test
    @DisplayName("세션 추적: 연결 시 SessionConnectEvent가 발생하고 세션이 등록된다")
    void sessionTracking_connectEventFires() throws Exception {
        int before = sessionTracker.getActiveSessionCount();

        StompSession session = StompTestSupport.connect(stompClient, port);

        // SessionConnectEvent → SessionConnectedEvent 순서로 발생
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(sessionTracker.getActiveSessionCount()).isGreaterThan(before));

        session.disconnect();
    }

    @Test
    @DisplayName("세션 추적: 연결 해제 시 SessionDisconnectEvent가 발생하고 세션이 제거된다")
    void sessionTracking_disconnectEventFires() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(sessionTracker.getActiveSessionCount()).isGreaterThan(0));

        int afterConnect = sessionTracker.getActiveSessionCount();

        // 연결 해제
        session.disconnect();

        // SessionDisconnectEvent 발생 → 세션 제거
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(sessionTracker.getActiveSessionCount()).isLessThan(afterConnect));
    }

    @Test
    @DisplayName("세션 추적: 여러 세션이 독립적으로 추적된다")
    void sessionTracking_multipleSessionsTrackedIndependently() throws Exception {
        StompSession session1 = StompTestSupport.connect(stompClient, port);
        StompSession session2 = StompTestSupport.connect(stompClient, port);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(sessionTracker.getActiveSessionCount()).isGreaterThanOrEqualTo(2));

        // session1만 해제
        session1.disconnect();

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(sessionTracker.getActiveSessions()).doesNotContain(session1.getSessionId()));

        // session2는 여전히 활성 상태
        assertThat(session2.isConnected()).isTrue();
        session2.disconnect();
    }

    @Test
    @DisplayName("에러 처리: @MessageExceptionHandler가 예외를 잡아 사용자에게 전달한다")
    void errorHandling_exceptionHandlerCatchesAndSendsToUser() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);

        BlockingQueue<String> errors = StompTestSupport.stringQueue();
        session.subscribe("/user/queue/errors", StompTestSupport.stringHandler(errors));
        Thread.sleep(200);

        // "error" 메시지를 보내면 NotificationController에서 IllegalArgumentException 발생
        session.send("/app/notify.error", "error");

        // GlobalStompExceptionHandler가 예외를 잡아 /user/queue/errors로 전달
        String errorMsg = StompTestSupport.poll(errors, 5);
        assertThat(errorMsg).isNotNull();
        assertThat(errorMsg).contains("INVALID_ARGUMENT");

        session.disconnect();
    }

    @Test
    @DisplayName("에러 처리: 정상 메시지는 예외 없이 처리된다")
    void errorHandling_normalMessageProcessedSuccessfully() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);

        BlockingQueue<String> results = StompTestSupport.stringQueue();
        session.subscribe("/topic/notifications", StompTestSupport.stringHandler(results));
        Thread.sleep(200);

        session.send("/app/notify.all", "Normal message");

        String result = StompTestSupport.poll(results, 5);
        assertThat(result).contains("Normal message");

        session.disconnect();
    }

    @Test
    @DisplayName("에러 처리: 에러는 발생시킨 세션에만 전달된다 (broadcast=false)")
    void errorHandling_errorSentOnlyToOriginatingSession() throws Exception {
        StompSession session1 = StompTestSupport.connect(stompClient, port);
        StompSession session2 = StompTestSupport.connect(stompClient, port);

        BlockingQueue<String> errors1 = StompTestSupport.stringQueue();
        BlockingQueue<String> errors2 = StompTestSupport.stringQueue();

        session1.subscribe("/user/queue/errors", StompTestSupport.stringHandler(errors1));
        session2.subscribe("/user/queue/errors", StompTestSupport.stringHandler(errors2));
        Thread.sleep(200);

        // session1에서 에러 유발
        session1.send("/app/notify.error", "error");

        // session1만 에러 수신
        assertThat(StompTestSupport.poll(errors1, 5)).isNotNull();
        // session2는 에러를 받지 않음
        assertThat(StompTestSupport.poll(errors2, 2)).isNull();

        session1.disconnect();
        session2.disconnect();
    }

    // ── SimpMessagingTemplate: 서버 → 클라이언트 push ──

    @Test
    @DisplayName("SimpMessagingTemplate: REST API에서 WebSocket 구독자에게 메시지를 push할 수 있다")
    void simpMessagingTemplate_pushFromRestToWebSocket() throws Exception {
        // 1. WebSocket 연결 + 구독
        StompSession session = StompTestSupport.connect(stompClient, port);
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/notifications", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // 2. HTTP POST 요청 — WebSocket이 아닌 일반 REST 호출
        //    서버의 PushController가 SimpMessagingTemplate.convertAndSend()를 호출한다
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/notifications"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("서버 점검 예정"))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 3. WebSocket 구독자가 메시지를 수신한다
        //    HTTP 요청을 보낸 클라이언트와 WebSocket 구독자는 완전히 별개다.
        //    서버가 "원하는 시점에" 구독자에게 메시지를 push한 것이다.
        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).isNotNull();
        assertThat(received).contains("서버 점검 예정");

        session.disconnect();
    }

    @Test
    @DisplayName("SimpMessagingTemplate: @SendTo와 같은 브로커를 공유한다 — 진입점만 다르다")
    void simpMessagingTemplate_sharesBrokerWithSendTo() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/notifications", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // 경로 1: @MessageMapping + @SendTo (클라이언트 → 서버 → 브로커 → 구독자)
        session.send("/app/notify.all", "WebSocket 경로");
        String fromWebSocket = StompTestSupport.poll(messages, 5);
        assertThat(fromWebSocket).contains("WebSocket 경로");

        // 경로 2: REST API → SimpMessagingTemplate (HTTP → 서버 → 브로커 → 구독자)
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/notifications"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("REST 경로"))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String fromRest = StompTestSupport.poll(messages, 5);
        assertThat(fromRest).contains("REST 경로");

        // 같은 /topic/notifications를 구독한 클라이언트가 두 경로의 메시지를 모두 수신한다.
        // SimpMessagingTemplate은 @SendTo와 동일한 brokerChannel을 사용한다.
        // 차이는 진입점뿐이다:
        //   @SendTo:               clientInboundChannel → handler → brokerChannel
        //   SimpMessagingTemplate:  직접 brokerChannel로 전송

        session.disconnect();
    }
}
