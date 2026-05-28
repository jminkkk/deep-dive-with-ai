package com.learning.websocket.stage2;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * Stage 2: 메시지 라우팅 패턴 — @SendTo, @SendToUser, @SubscribeMapping 비교.
 *
 * <p>핵심 차이:
 * <ul>
 *   <li>{@code @SendTo("/topic/chat")} — 모든 구독자에게 브로드캐스트</li>
 *   <li>{@code @SendToUser("/queue/reply")} — 메시지를 보낸 사용자에게만 응답</li>
 *   <li>{@code @SubscribeMapping} — 구독 시점에 초기 데이터를 직접 반환 (브로커를 거치지 않음)</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html">
 *      Annotated Controllers</a>
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html">
 *      User Destinations</a>
 */
@Controller
public class ChatController {

    /**
     * 패턴 1: 브로드캐스트 — 모든 구독자에게 전달.
     *
     * <p>클라이언트가 /app/chat.send로 SEND하면,
     * 반환값이 /topic/chat으로 브로드캐스트된다.
     */
    @MessageMapping("/chat.send")
    @SendTo("/topic/chat")
    public String broadcastMessage(String message, Principal principal) {
        String sender = (principal != null) ? principal.getName() : "anonymous";
        return sender + ": " + message;
    }

    /**
     * 패턴 2: 개인 응답 — 보낸 사용자에게만 반환.
     *
     * <p>@SendToUser는 내부적으로 destination을 변환한다:
     * {@code /queue/reply} → {@code /user/{sessionId}/queue/reply}
     *
     * <p>클라이언트는 {@code /user/queue/reply}를 구독해야 한다.
     * (Spring이 /user/{sessionId}를 자동으로 처리)
     *
     * <p>주의: broadcast 속성 (기본값 true)
     * - true: 같은 사용자의 모든 세션(탭)에 전달
     * - false: 메시지를 보낸 세션에만 전달
     */
    @MessageMapping("/chat.private")
    @SendToUser("/queue/reply")
    public String privateReply(String message) {
        return "서버 응답: " + message;
    }

    /**
     * 패턴 3: 구독 시 초기 데이터 반환 — 브로커를 거치지 않는다.
     *
     * <p>주의: @SubscribeMapping의 기본 동작은 @MessageMapping과 다르다!
     * - @MessageMapping: 반환값이 brokerChannel → 구독자에게 배포
     * - @SubscribeMapping: 반환값이 clientOutboundChannel → 구독한 클라이언트에게 직접 반환
     *
     * <p>이 차이를 모르면 "다른 클라이언트에게 전달이 안 된다"는 버그로 오인할 수 있다.
     * 브로커를 거쳐 브로드캐스트하려면 @SendTo를 명시해야 한다.
     */
    @SubscribeMapping("/chat.history")
    public String onSubscribe() {
        return "시스템: 최근 채팅 기록이 없습니다. 새로운 메시지를 보내보세요";
    }

    /**
     * 함정 시연용: Map 반환 → application/json으로 직렬화된다.
     *
     * <p>StringMessageConverter를 사용하는 클라이언트는 이 응답을 수신하지 못한다.
     * 서버 로그에도 에러가 없고, 클라이언트에도 에러가 없다.
     * 메시지가 조용히 사라지는 "silent failure"가 발생한다.
     *
     * <p>원인: 서버가 Map을 반환하면 MappingJackson2MessageConverter가 처리하여
     * content-type이 application/json이 된다. 클라이언트의 StringMessageConverter는
     * text/plain만 처리하므로 이 메시지를 무시한다.
     */
    @MessageMapping("/chat.info")
    @SendTo("/topic/chat.info")
    public Map<String, String> chatInfo(String message) {
        return Map.of("content", message, "type", "INFO");
    }

    /**
     * 패턴 4: 경로 변수 사용 — 채팅방 ID를 destination에 포함.
     *
     * <p>Ant 스타일 패턴을 지원한다: /chat.room.{roomId}
     * @DestinationVariable로 경로 변수를 추출할 수 있다.
     */
    @MessageMapping("/chat.room.{roomId}")
    @SendTo("/topic/room.{roomId}")
    public String roomMessage(@DestinationVariable String roomId, String message) {
        return "[Room " + roomId + "] " + message;
    }

    /**
     * 패턴 5: 에러 핸들링 — @MessageExceptionHandler.
     *
     * <p>@SendToUser + broadcast=false 조합: 에러를 발생시킨 세션에만 전달.
     * 다른 탭/세션에는 에러가 전파되지 않는다.
     */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public String handleException(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
        return "[" + ex.getClass().getSimpleName() + "] " + message;
    }
}