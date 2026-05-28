package com.learning.websocket.stage5;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 5: 스케일링 & 성능 — 프로덕션 환경의 제약과 해결책.
 *
 * <p>이 Stage에서 학습하는 내용:
 * <ul>
 *   <li>SimpleBroker의 한계 — 단일 인스턴스, 메모리 기반</li>
 *   <li>메시지 크기 제한 — sendBufferSizeLimit, messageSizeLimit</li>
 *   <li>스레드 풀 설정 — ThreadPoolExecutor의 queueCapacity 함정</li>
 *   <li>외부 브로커 전환 — RabbitMQ/ActiveMQ 설정</li>
 * </ul>
 *
 * <p>실제 프로덕션 환경과의 차이:
 * <ul>
 *   <li>이 테스트는 SimpleBroker를 사용 — 외부 브로커 없이도 실행 가능</li>
 *   <li>실제 스케일링 테스트는 다중 인스턴스 + 외부 브로커가 필요</li>
 *   <li>부하 테스트는 k6, JMeter 같은 전용 도구를 사용해야 한다</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Stage5_ScalingTest.TestApp.class
)
class Stage5_ScalingTest {

    @SpringBootApplication(
            scanBasePackages = "com.learning.websocket.stage5",
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
    @DisplayName("SimpleBroker 확인: 단일 인스턴스에서는 정상 동작한다")
    void simpleBroker_worksOnSingleInstance() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/echo", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        session.send("/app/echo", "Hello");

        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).isEqualTo("echo: Hello");

        session.disconnect();
    }

    @Test
    @DisplayName("다중 클라이언트: 여러 클라이언트가 동시에 메시지를 주고받을 수 있다")
    void multipleClients_concurrentMessaging() throws Exception {
        int clientCount = 5;
        List<StompSession> sessions = new ArrayList<>();
        List<BlockingQueue<String>> queues = new ArrayList<>();

        // 여러 클라이언트 동시 연결 + 구독
        for (int i = 0; i < clientCount; i++) {
            StompSession session = StompTestSupport.connect(stompClient, port);
            BlockingQueue<String> queue = StompTestSupport.stringQueue();
            session.subscribe("/topic/echo", StompTestSupport.stringHandler(queue));
            sessions.add(session);
            queues.add(queue);
        }
        Thread.sleep(300);

        // 첫 번째 클라이언트가 메시지 전송
        sessions.get(0).send("/app/echo", "Multi-client test");

        // 모든 클라이언트가 메시지를 수신해야 한다
        for (int i = 0; i < clientCount; i++) {
            String received = StompTestSupport.poll(queues.get(i), 5);
            assertThat(received).isEqualTo("echo: Multi-client test");
        }

        sessions.forEach(StompSession::disconnect);
    }

    @Test
    @DisplayName("동시 메시지 처리: I/O 바운드 작업이 있어도 메시지가 유실되지 않는다")
    void concurrentMessages_noMessageLoss() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/slow", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        int messageCount = 10;

        // 동시에 여러 메시지 전송
        for (int i = 0; i < messageCount; i++) {
            session.send("/app/slow", ("msg-" + i));
        }

        // 모든 메시지가 처리되어야 한다 (순서는 보장되지 않을 수 있음)
        AtomicInteger received = new AtomicInteger(0);
        for (int i = 0; i < messageCount; i++) {
            String msg = StompTestSupport.poll(messages, 15);
            if (msg != null) received.incrementAndGet();
        }

        assertThat(received.get()).isEqualTo(messageCount);
        session.disconnect();
    }

    @Test
    @DisplayName("메시지 크기 제한: 설정된 제한 이내의 큰 메시지가 정상 처리된다")
    void messageSizeLimit_withinLimitSucceeds() throws Exception {
        StompSession session = StompTestSupport.connect(stompClient, port);
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/echo", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // 4KB 메시지 — Tomcat 기본 WebSocket 버퍼(8KB) 이내
        // 주의: Tomcat의 기본 텍스트 메시지 버퍼는 8KB이다.
        // 이를 초과하려면 ServerEndpointConfig 또는 application.yml에서 별도 설정이 필요하다.
        // 예: server.tomcat.max-text-message-buffer-size=131072
        String largeMessage = "A".repeat(4 * 1024);
        session.send("/app/echo", largeMessage);

        String received = StompTestSupport.poll(messages, 10);
        assertThat(received).isNotNull();
        assertThat(received).startsWith("echo: ");

        session.disconnect();
    }

    @Test
    @DisplayName("연결/해제 반복: 빠른 연결/해제가 리소스 누수 없이 처리된다")
    void rapidConnectDisconnect_noResourceLeak() throws Exception {
        int iterations = 10;
        CountDownLatch latch = new CountDownLatch(iterations);

        for (int i = 0; i < iterations; i++) {
            StompSession session = StompTestSupport.connect(stompClient, port);
            assertThat(session.isConnected()).isTrue();
            session.disconnect();
            latch.countDown();
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

        // 최종 연결이 정상적으로 가능한지 확인 (리소스 누수 없음)
        StompSession finalSession = StompTestSupport.connect(stompClient, port);
        assertThat(finalSession.isConnected()).isTrue();
        finalSession.disconnect();
    }
}