package com.learning.websocket.stage0;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 0: Raw WebSocket의 고통을 체험한다.
 *
 * <p>이 테스트를 통해 STOMP 없이 WebSocket만 사용할 때의 문제점을 직접 확인한다:
 * <ul>
 *   <li>메시지 포맷을 직접 정의하고 파싱해야 한다</li>
 *   <li>라우팅 로직을 직접 구현해야 한다</li>
 *   <li>브로드캐스트를 위한 세션 관리를 직접 해야 한다</li>
 *   <li>에러 응답 포맷이 비표준이다</li>
 * </ul>
 *
 * <p>Stage 1에서 STOMP가 이 모든 문제를 어떻게 해결하는지 확인하라.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Stage0_RawWebSocketTest.TestApp.class
)
class Stage0_RawWebSocketTest {

    @SpringBootApplication(
            scanBasePackages = "com.learning.websocket.stage0",
            exclude = SecurityAutoConfiguration.class
    )
    @Import(RawWebSocketConfig.class)
    static class TestApp {}

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession session;

    @BeforeEach
    void setUp() throws Exception {
        BlockingQueue<String> sink = new LinkedBlockingQueue<>();
        session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(@NonNull WebSocketSession s, TextMessage msg) {
                        sink.add(msg.getPayload());
                    }
                }, "ws://localhost:" + port + "/raw-ws")
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    @Test
    @DisplayName("문제 1: 메시지를 JSON으로 직접 구성해야 한다 — 타입 안전성이 없다")
    void problem1_manualJsonConstruction() throws Exception {
        // Raw WebSocket에서는 메시지 포맷이 없다.
        // 클라이언트와 서버가 JSON 형식을 "약속"해야 하고,
        // 약속이 깨지면 런타임에서야 에러를 발견한다.

        // Given: 올바른 형식의 JSON
        String validJson = objectMapper.writeValueAsString(Map.of(
                "type", "CHAT",
                "content", "Hello",
                "sender", "testUser"
        ));

        // When: 전송
        session.sendMessage(new TextMessage(validJson));

        // Then: 응답도 JSON 문자열 — 직접 파싱해야 한다
        // (STOMP에서는 @MessageMapping + 타입 변환이 자동)
    }

    @Test
    @DisplayName("문제 2: 잘못된 JSON을 보내면 서버가 직접 에러를 처리해야 한다")
    void problem2_invalidJsonHandling() throws Exception {
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        WebSocketSession errorSession = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(@NonNull WebSocketSession s, TextMessage msg) {
                        responses.add(msg.getPayload());
                    }
                }, "ws://localhost:" + port + "/raw-ws")
                .get(5, TimeUnit.SECONDS);

        // When: 유효하지 않은 JSON 전송
        errorSession.sendMessage(new TextMessage("이것은 JSON이 아닙니다"));

        // Then: 서버가 직접 에러 응답을 만들어 보내야 한다
        String response = responses.poll(5, TimeUnit.SECONDS);
        assertThat(response).contains("INVALID_JSON");

        // STOMP에서는 프로토콜 레벨에서 ERROR 프레임이 표준화되어 있다
        errorSession.close();
    }

    @Test
    @DisplayName("문제 3: 알 수 없는 메시지 타입에 대한 라우팅을 switch/case로 처리한다")
    void problem3_manualRouting() throws Exception {
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        WebSocketSession routeSession = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(@NonNull WebSocketSession s, TextMessage msg) {
                        responses.add(msg.getPayload());
                    }
                }, "ws://localhost:" + port + "/raw-ws")
                .get(5, TimeUnit.SECONDS);

        // When: 서버가 모르는 타입의 메시지 전송
        String unknownType = objectMapper.writeValueAsString(Map.of(
                "type", "UNKNOWN_ACTION",
                "content", "test"
        ));
        routeSession.sendMessage(new TextMessage(unknownType));

        // Then: switch default에 걸려서 에러 응답
        String response = responses.poll(5, TimeUnit.SECONDS);
        assertThat(response).contains("UNKNOWN_TYPE");

        // STOMP에서는 @MessageMapping이 destination 기반으로 자동 라우팅한다
        routeSession.close();
    }

    @Test
    @DisplayName("문제 4: DM(개인 메시지)을 보내려면 세션 ID를 직접 알아야 한다")
    void problem4_directMessageRequiresSessionId() throws Exception {
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        WebSocketSession dmSession = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(@NonNull WebSocketSession s, TextMessage msg) {
                        responses.add(msg.getPayload());
                    }
                }, "ws://localhost:" + port + "/raw-ws")
                .get(5, TimeUnit.SECONDS);

        // 존재하지 않는 세션에 DM 시도
        String dmMessage = objectMapper.writeValueAsString(Map.of(
                "type", "DM",
                "targetSessionId", "non-existent-session",
                "content", "Hello",
                "sender", "testUser"
        ));
        dmSession.sendMessage(new TextMessage(dmMessage));

        String response = responses.poll(5, TimeUnit.SECONDS);
        assertThat(response).contains("TARGET_NOT_FOUND");

        // STOMP에서는 @SendToUser가 사용자 식별을 자동으로 처리한다
        dmSession.close();
    }

    @Test
    @DisplayName("문제 5: 브로드캐스트를 위해 모든 세션을 직접 순회해야 한다")
    void problem5_manualBroadcast() throws Exception {
        // 두 번째 클라이언트 연결
        BlockingQueue<String> client2Messages = new LinkedBlockingQueue<>();
        WebSocketSession client2 = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(@NonNull WebSocketSession s, TextMessage msg) {
                        client2Messages.add(msg.getPayload());
                    }
                }, "ws://localhost:" + port + "/raw-ws")
                .get(5, TimeUnit.SECONDS);

        // client1이 채팅 메시지 전송
        String chatMessage = objectMapper.writeValueAsString(Map.of(
                "type", "CHAT",
                "content", "Hello everyone",
                "sender", "client1"
        ));
        session.sendMessage(new TextMessage(chatMessage));

        // client2도 메시지를 받아야 한다 (서버가 직접 순회해서 전송)
        String received = client2Messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, String> parsed = objectMapper.readValue(received, Map.class);
        assertThat(parsed.get("content")).isEqualTo("Hello everyone");
        assertThat(parsed.get("sender")).isEqualTo("client1");

        // STOMP에서는 @SendTo("/topic/chat")이면 구독자에게 자동 배포된다
        client2.close();
    }
}