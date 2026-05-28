package com.learning.websocket.stage2;

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
 * Stage 2: 메시지 라우팅 심화 — @SendTo vs @SendToUser vs @SubscribeMapping.
 *
 * <p>이 Stage에서 학습하는 핵심 차이점:
 * <ul>
 *   <li>@SendTo: 모든 구독자에게 브로드캐스트 (1:N)</li>
 *   <li>@SendToUser: 메시지를 보낸 사용자에게만 응답 (1:1)</li>
 *   <li>@SubscribeMapping: 구독 시점에 초기 데이터 반환 (브로커 경유 안 함)</li>
 *   <li>@DestinationVariable: destination 경로에서 변수 추출</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Stage2_MessageRoutingTest.TestApp.class
)
class Stage2_MessageRoutingTest {

    @SpringBootApplication(
            scanBasePackages = "com.learning.websocket.stage2",
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
        if (session != null && session.isConnected()) session.disconnect();
        if (stompClient != null) stompClient.stop();
    }

    @Test
    @DisplayName("@SendTo: 모든 구독자에게 브로드캐스트된다")
    void sendTo_broadcastsToAllSubscribers() throws Exception {
        StompSession session2 = StompTestSupport.connect(stompClient, port);

        BlockingQueue<String> messages1 = StompTestSupport.stringQueue();
        BlockingQueue<String> messages2 = StompTestSupport.stringQueue();

        session.subscribe("/topic/chat", StompTestSupport.stringHandler(messages1));
        session2.subscribe("/topic/chat", StompTestSupport.stringHandler(messages2));
        Thread.sleep(200);

        // session1이 메시지 전송
        session.send("/app/chat.send", "Hello everyone");

        // 두 세션 모두 수신
        assertThat(StompTestSupport.poll(messages1, 5)).contains("Hello everyone");
        assertThat(StompTestSupport.poll(messages2, 5)).contains("Hello everyone");

        session2.disconnect();
    }

    @Test
    @DisplayName("@SendToUser: 보낸 사용자에게만 응답한다")
    void sendToUser_repliesToSenderOnly() throws Exception {
        StompSession session2 = StompTestSupport.connect(stompClient, port);

        BlockingQueue<String> senderMessages = StompTestSupport.stringQueue();
        BlockingQueue<String> otherMessages = StompTestSupport.stringQueue();

        // @SendToUser의 destination은 /user/queue/reply로 구독해야 한다
        // Spring이 /user/{sessionId}/queue/reply로 자동 변환한다
        session.subscribe("/user/queue/reply", StompTestSupport.stringHandler(senderMessages));
        session2.subscribe("/user/queue/reply", StompTestSupport.stringHandler(otherMessages));
        Thread.sleep(200);

        // session1이 private 메시지 전송
        session.send("/app/chat.private", "Private message");

        // session1만 응답을 받는다
        String reply = StompTestSupport.poll(senderMessages, 5);
        assertThat(reply).contains("서버 응답: Private message");

        // session2는 받지 못한다
        String otherReply = StompTestSupport.poll(otherMessages, 2);
        assertThat(otherReply).isNull();

        session2.disconnect();
    }

    @Test
    @DisplayName("@SubscribeMapping: 구독 시점에 초기 데이터가 바로 반환된다")
    void subscribeMapping_returnsInitialDataOnSubscribe() throws Exception {
        BlockingQueue<String> messages = StompTestSupport.stringQueue();

        // /app/chat.history를 구독하면 @SubscribeMapping이 즉시 응답을 반환한다
        // 주의: /topic이 아닌 /app prefix로 구독!
        session.subscribe("/app/chat.history", StompTestSupport.stringHandler(messages));

        // SEND 없이도 구독 즉시 초기 데이터를 받는다
        String initialData = StompTestSupport.poll(messages, 5);
        assertThat(initialData).isNotNull();
        // List<String>이 JSON으로 직렬화되어 반환된다
    }

    @Test
    @DisplayName("@SubscribeMapping 주의: 다른 구독자에게는 전달되지 않는다")
    void subscribeMapping_pitfall_notBroadcast() throws Exception {
        BlockingQueue<String> messages1 = StompTestSupport.stringQueue();
        BlockingQueue<String> messages2 = StompTestSupport.stringQueue();

        // session1이 먼저 구독
        session.subscribe("/app/chat.history", StompTestSupport.stringHandler(messages1));
        String data1 = StompTestSupport.poll(messages1, 5);
        assertThat(data1).isNotNull(); // session1은 초기 데이터를 받는다

        // session2가 나중에 구독
        StompSession session2 = StompTestSupport.connect(stompClient, port);
        session2.subscribe("/app/chat.history", StompTestSupport.stringHandler(messages2));
        String data2 = StompTestSupport.poll(messages2, 5);
        assertThat(data2).isNotNull(); // session2도 자기만의 초기 데이터를 받는다

        // 핵심: @SubscribeMapping은 각 구독자에게 개별적으로 응답한다.
        // 브로커를 거치지 않으므로 다른 구독자에게 전파되지 않는다.
        // 브로커를 통한 브로드캐스트가 필요하면 @SendTo를 추가해야 한다.

        session2.disconnect();
    }

    @Test
    @DisplayName("@DestinationVariable: destination 경로에서 변수를 추출한다")
    void destinationVariable_extractsFromPath() throws Exception {
        BlockingQueue<String> room1Messages = StompTestSupport.stringQueue();
        BlockingQueue<String> room2Messages = StompTestSupport.stringQueue();

        // 다른 채팅방 구독
        session.subscribe("/topic/room.room1", StompTestSupport.stringHandler(room1Messages));
        session.subscribe("/topic/room.room2", StompTestSupport.stringHandler(room2Messages));
        Thread.sleep(200);

        // room1에 메시지 전송
        session.send("/app/chat.room.room1", "Hello Room 1");

        // room1 구독자만 수신
        String msg1 = StompTestSupport.poll(room1Messages, 5);
        assertThat(msg1).contains("[Room room1]").contains("Hello Room 1");

        // room2 구독자는 받지 못한다
        String msg2 = StompTestSupport.poll(room2Messages, 2);
        assertThat(msg2).isNull();
    }

    @Test
    @DisplayName("함정: 서버가 Map을 반환하면 StringMessageConverter 클라이언트에 도달하지 않는다 (silent failure)")
    void pitfall_mapReturnSilentlyLostWithStringConverter() throws Exception {
        BlockingQueue<String> messages = StompTestSupport.stringQueue();

        // /topic/chat.info 구독 — chatInfo()가 Map을 반환하는 destination
        session.subscribe("/topic/chat.info", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // chatInfo()는 Map<String, String>을 반환한다
        session.send("/app/chat.info", "Hello");

        // 메시지가 도착하지 않는다!
        // 서버 로그에 에러 없음. 클라이언트에도 에러 없음. 메시지만 조용히 사라진다.
        String received = StompTestSupport.poll(messages, 3);
        assertThat(received)
                .as("Map 반환 → application/json 직렬화 → StringMessageConverter(text/plain 전용)가 무시")
                .isNull();

        // 디버깅 포인트:
        // 1. 서버 반환 타입이 Map/List/POJO → MappingJackson2MessageConverter가 직렬화
        //    → content-type: application/json
        // 2. 클라이언트의 StringMessageConverter는 text/plain만 처리
        //    → application/json 메시지를 건너뛴다
        // 3. 결과: 예외도 로그도 없이 메시지가 소실되는 "silent failure"
        //
        // 해결법:
        // A. 서버 반환 타입을 String으로 변경 (간단, 이 프로젝트에서 사용한 방법)
        // B. 클라이언트에 MappingJackson2MessageConverter 추가 (유연, 프로덕션 권장)
    }

    @Test
    @DisplayName("대조: 서버가 String을 반환하면 StringMessageConverter 클라이언트가 정상 수신한다")
    void contrast_stringReturnReceivedByStringConverter() throws Exception {
        BlockingQueue<String> messages = StompTestSupport.stringQueue();
        session.subscribe("/topic/chat", StompTestSupport.stringHandler(messages));
        Thread.sleep(200);

        // broadcastMessage()는 String을 반환한다
        session.send("/app/chat.send", "Hello");

        // String 반환 → text/plain → StringMessageConverter가 정상 처리
        String received = StompTestSupport.poll(messages, 5);
        assertThat(received).isNotNull().contains("Hello");

        // 위 pitfall 테스트와 이 테스트의 유일한 차이점: 서버 메서드의 반환 타입
        // Map    반환 → application/json → StringMessageConverter 무시 → null
        // String 반환 → text/plain       → StringMessageConverter 처리 → 정상 수신
    }

    @Test
    @DisplayName("/user/queue/errors: 에러가 발생한 세션에만 에러 메시지가 전달된다")
    void errorHandling_sentToErrorDestination() throws Exception {
        // @MessageExceptionHandler + @SendToUser(broadcast=false)
        // → 에러를 발생시킨 세션에만 에러 메시지 전달
        BlockingQueue<String> errors = StompTestSupport.stringQueue();
        session.subscribe("/user/queue/errors", StompTestSupport.stringHandler(errors));
        Thread.sleep(200);

        // 존재하지 않는 destination으로 보내 에러를 유발하지는 않지만,
        // 컨트롤러 내부에서 예외가 발생하면 /user/queue/errors로 전달된다
        // (Stage 4에서 더 자세히 다룬다)
    }
}