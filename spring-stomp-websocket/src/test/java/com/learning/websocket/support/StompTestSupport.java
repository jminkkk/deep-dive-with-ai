package com.learning.websocket.support;

import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * STOMP 테스트 공통 유틸리티.
 *
 * <p>각 Stage 테스트에서 반복되는 STOMP 클라이언트 설정,
 * 연결, 구독, 메시지 수신 로직을 추상화한다.
 *
 * <p>주의: StringMessageConverter는 text/plain content-type만 처리한다.
 * 서버가 application/json으로 응답하면(Map, List 등의 반환 타입)
 * 클라이언트에서 수신되지 않는다. 이는 STOMP 클라이언트 설정 시
 * 자주 겪는 함정이다.
 */
public final class StompTestSupport {

    private StompTestSupport() {}

    /**
     * String 메시지용 STOMP 클라이언트 생성.
     *
     * <p>StringMessageConverter 사용:
     * - 전송: String → byte[] 변환
     * - 수신: byte[] → String 변환 (text/plain content-type만)
     */
    public static WebSocketStompClient createStringClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        return client;
    }

    /** 기본 연결 (인증 없음) */
    public static StompSession connect(WebSocketStompClient client, int port) throws Exception {
        return client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    /** 커스텀 헤더 포함 연결 (인증 토큰 등) */
    public static StompSession connectWithHeaders(
            WebSocketStompClient client, int port, Map<String, String> headers) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        headers.forEach(connectHeaders::add);

        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        return client.connectAsync(
                "ws://localhost:" + port + "/ws",
                httpHeaders, connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    /**
     * 문자열 메시지 수신 핸들러.
     *
     * <p>String.class를 payload type으로 요청한다.
     * StringMessageConverter가 text/plain 메시지를 String으로 변환한다.
     */
    public static StompFrameHandler stringHandler(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                if (payload != null) {
                    queue.add(payload.toString());
                }
            }
        };
    }

    /** 지정 시간 내 메시지 수신 대기 */
    public static <T> T poll(BlockingQueue<T> queue, long seconds) throws InterruptedException {
        return queue.poll(seconds, TimeUnit.SECONDS);
    }

    public static BlockingQueue<String> stringQueue() {
        return new LinkedBlockingQueue<>();
    }
}