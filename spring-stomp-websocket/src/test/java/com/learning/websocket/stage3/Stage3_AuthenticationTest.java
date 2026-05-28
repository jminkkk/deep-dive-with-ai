package com.learning.websocket.stage3;

import com.learning.websocket.support.StompTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 3: 인증/인가 — 보안 구멍을 발견하고 막는다.
 *
 * <p>이전 Stage(0~2)에서는 누구나 연결하고 메시지를 보낼 수 있었다.
 * 이 Stage에서는:
 * <ul>
 *   <li>STOMP CONNECT 시 인증 토큰 검증</li>
 *   <li>destination 레벨 인가 (admin 전용 채널)</li>
 *   <li>Principal을 통한 사용자 식별</li>
 * </ul>
 *
 * <p>핵심 학습 포인트:
 * <ul>
 *   <li>Spring은 STOMP login/passcode 헤더를 기본적으로 무시한다</li>
 *   <li>인증은 HTTP 핸드셰이크 또는 ChannelInterceptor에서 처리한다</li>
 *   <li>ChannelInterceptor에서 예외를 던지면 연결이 즉시 종료된다</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Stage3_AuthenticationTest.TestApp.class
)
class Stage3_AuthenticationTest {

    @SpringBootApplication(
            scanBasePackages = "com.learning.websocket.stage3",
            exclude = SecurityAutoConfiguration.class
    )
    static class TestApp {}

    @LocalServerPort
    private int port;

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
    @DisplayName("인증 성공: 유효한 토큰으로 CONNECT하면 세션이 생성된다")
    void authentication_successWithValidToken() throws Exception {
        // STOMP CONNECT 프레임에 Authorization 헤더 포함
        StompSession session = StompTestSupport.connectWithHeaders(
                stompClient, port, Map.of("Authorization", "token-alice"));

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("인증 실패: 토큰 없이 CONNECT하면 연결이 거부된다")
    void authentication_failsWithoutToken() {
        // 토큰 없이 연결 시도 → ChannelInterceptor에서 SecurityException
        // → STOMP ERROR 프레임 전송 → 연결 종료
        assertThatThrownBy(() ->
                StompTestSupport.connect(stompClient, port)
        ).isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("인증 실패: 잘못된 토큰으로 CONNECT하면 연결이 거부된다")
    void authentication_failsWithInvalidToken() {
        assertThatThrownBy(() ->
                StompTestSupport.connectWithHeaders(
                        stompClient, port, Map.of("Authorization", "invalid-token"))
        ).isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("인가 성공: 인증된 사용자가 일반 채널에 메시지를 보낼 수 있다")
    void authorization_authenticatedUserCanSendToPublicChannel() throws Exception {
        StompSession session = StompTestSupport.connectWithHeaders(
                stompClient, port, Map.of("Authorization", "token-alice"));

        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/secure", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        session.send("/app/secure.chat", "Hello from alice");

        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).contains("alice").contains("Hello from alice");

        session.disconnect();
    }

    @Test
    @DisplayName("인가: admin 사용자만 관리자 채널에 접근할 수 있다")
    void authorization_onlyAdminCanAccessAdminChannel() throws Exception {
        // admin 토큰으로 연결
        StompSession adminSession = StompTestSupport.connectWithHeaders(
                stompClient, port, Map.of("Authorization", "token-admin"));

        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        adminSession.subscribe("/topic/admin", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        adminSession.send("/app/admin.broadcast", "Admin message");

        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).contains("[ADMIN]").contains("Admin message");

        adminSession.disconnect();
    }

    @Test
    @DisplayName("Principal: 인증 정보가 컨트롤러 메서드에 자동 주입된다")
    void principal_injectedIntoController() throws Exception {
        StompSession session = StompTestSupport.connectWithHeaders(
                stompClient, port, Map.of("Authorization", "token-bob"));

        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        // @SendToUser 사용 → /user/queue/identity 구독
        session.subscribe("/user/queue/identity", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        session.send("/app/secure.whoami", "");

        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).contains("bob");

        session.disconnect();
    }

    @Test
    @DisplayName("주의: STOMP login/passcode 헤더는 Spring에서 무시된다")
    void pitfall_stompLoginHeadersIgnored() {
        // STOMP 스펙의 login/passcode는 STOMP-over-TCP용이다.
        // Spring WebSocket에서는 이 헤더를 무시한다.
        // 대신 커스텀 헤더(Authorization 등)를 사용해야 한다.

        // login/passcode만 보내고 Authorization은 안 보내면 → 인증 실패
        assertThatThrownBy(() ->
                StompTestSupport.connectWithHeaders(
                        stompClient, port,
                        Map.of("login", "alice", "passcode", "password"))
        ).isInstanceOf(ExecutionException.class);
    }
}
