package com.learning.websocket.stage1;

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

import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 1: STOMP 도입 — Stage 0의 모든 문제가 해결된다.
 *
 * <p>개선점:
 * <ul>
 *   <li>JSON 파싱 자동화 — MessageConverter가 처리</li>
 *   <li>라우팅 자동화 — @MessageMapping이 destination 기반으로 라우팅</li>
 *   <li>브로드캐스트 자동화 — @SendTo로 구독자에게 자동 배포</li>
 *   <li>에러 프레임 표준화 — STOMP ERROR 프레임</li>
 * </ul>
 *
 * <p>주의할 점 (이 Stage에서 학습):
 * <ul>
 *   <li>/app prefix는 @MessageMapping 앞에 자동으로 붙는다</li>
 *   <li>@SendTo 생략 시 기본 destination 규칙을 이해해야 한다</li>
 *   <li>구독(SUBSCRIBE)과 전송(SEND)의 destination이 다르다</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Stage1_StompBasicTest.TestApp.class
)
class Stage1_StompBasicTest {

    @SpringBootApplication(
            scanBasePackages = "com.learning.websocket.stage1",
            exclude = SecurityAutoConfiguration.class
    )
    static class TestApp {}

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() throws Exception {
        stompClient = StompTestSupport.createStringClient();
        session = StompTestSupport.connect(stompClient, port);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    @DisplayName("개선: @MessageMapping + @SendTo로 라우팅과 브로드캐스트가 자동화된다")
    void improvement_automaticRoutingAndBroadcast() throws Exception {
        // Given: /topic/greetings를 구독
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/greetings", StompTestSupport.stringHandler(messages));
        Thread.sleep(200); // 구독 완료 대기

        // When: /app/greeting으로 메시지 전송
        // - /app prefix가 @MessageMapping("/greeting")과 매칭
        // - 반환값이 @SendTo("/topic/greetings")로 자동 브로드캐스트
        session.send("/app/greeting", "Hello STOMP!");

        // Then: 구독자에게 메시지가 자동 전달된다
        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).isNotNull();
        assertThat(received).contains("Hello STOMP!");

        // Stage 0과 비교: JSON 수동 파싱, switch 라우팅, 세션 순회가 모두 사라졌다
    }

    @Test
    @DisplayName("개선: 여러 구독자에게 자동 브로드캐스트된다")
    void improvement_autoBroadcastToMultipleSubscribers() throws Exception {
        // 두 번째 세션 연결
        StompSession session2 = StompTestSupport.connect(stompClient, port);

        BlockingQueue<String> messages1 = StompTestSupport.stringQueue();
        BlockingQueue<String> messages2 = StompTestSupport.stringQueue();

        session.subscribe("/topic/greetings", StompTestSupport.stringHandler(messages1));
        session2.subscribe("/topic/greetings", StompTestSupport.stringHandler(messages2));
        Thread.sleep(200);

        // When: 한 세션이 메시지 전송
        session.send("/app/greeting", "Broadcast test");

        // Then: 두 구독자 모두 메시지를 받는다
        String msg1 = StompTestSupport.poll(messages1, 5);
        String msg2 = StompTestSupport.poll(messages2, 5);
        assertThat(msg1).contains("Broadcast test");
        assertThat(msg2).contains("Broadcast test");

        session2.disconnect();
    }

    @Test
    @DisplayName("주의: 구독하지 않은 destination의 메시지는 받지 못한다")
    void pitfall_mustSubscribeToReceive() throws Exception {
        // /topic/other를 구독 (greetings가 아님)
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/other", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // /app/greeting으로 전송 → @SendTo("/topic/greetings")로 라우팅
        session.send("/app/greeting", "Where does this go?");

        // /topic/other를 구독했으므로 /topic/greetings 메시지는 받지 못한다
        String received = StompTestSupport.poll(messages, 2);
        assertThat(received).isNull();
    }

    @Test
    @DisplayName("주의: /app prefix 없이 보내면 @MessageMapping에 도달하지 않는다")
    void pitfall_destinationPrefixRequired() throws Exception {
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/greetings", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // /greeting으로 보냄 (/app/greeting이 아님)
        // → applicationDestinationPrefix에 매칭되지 않아 @MessageMapping에 도달하지 않는다
        // → 브로커로 직접 가지만, /greeting은 브로커 prefix(/topic, /queue)도 아님
        session.send("/greeting", "Without prefix");

        String received = StompTestSupport.poll(messages, 2);
        assertThat(received).isNull();

        // 올바른 경로: /app/greeting
    }

    @Test
    @DisplayName("핵심: destination prefix 라우팅 규칙 정리")
    void concept_destinationPrefixRouting() throws Exception {
        /*
         * Spring STOMP의 destination 라우팅 규칙:
         *
         * 클라이언트 SEND destination    →  처리 방식
         * ─────────────────────────────────────────────────
         * /app/greeting                 →  @MessageMapping("/greeting") 메서드로 라우팅
         * /topic/greetings             →  브로커가 직접 처리 (구독자에게 전달)
         * /queue/something             →  브로커가 직접 처리
         * /greeting                    →  어디에도 매칭 안 됨 (무시)
         *
         * 클라이언트 SUBSCRIBE destination →  처리 방식
         * ─────────────────────────────────────────────────
         * /topic/greetings             →  브로커에 구독 등록
         * /app/something               →  @SubscribeMapping으로 라우팅 (Stage 2에서 학습)
         *
         * 핵심: SEND는 /app prefix로, SUBSCRIBE는 /topic 또는 /queue prefix로!
         */
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/greetings", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        session.send("/app/greeting", "Prefix test");
        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).contains("Prefix test");
    }
}