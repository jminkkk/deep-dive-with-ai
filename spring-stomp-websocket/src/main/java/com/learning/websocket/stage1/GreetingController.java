package com.learning.websocket.stage1;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Stage 1: STOMP 기본 컨트롤러.
 *
 * <p>Stage 0에서 직접 구현했던 라우팅/파싱/브로드캐스트를
 * {@code @MessageMapping}과 {@code @SendTo} 애노테이션이 모두 대체한다.
 *
 * <p>메시지 흐름:
 * <pre>
 * 클라이언트 SEND "/app/greeting"
 *   → clientInboundChannel
 *   → SimpAnnotationMethodMessageHandler가 @MessageMapping("/greeting") 매칭
 *   → handle() 실행
 *   → 반환값이 brokerChannel로 전달
 *   → SimpleBrokerMessageHandler가 "/topic/greetings" 구독자에게 배포
 *   → clientOutboundChannel
 *   → 각 구독 클라이언트에게 MESSAGE 프레임 전송
 * </pre>
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html">
 *      Annotated Controllers</a>
 */
@Controller
public class GreetingController {

    /**
     * /app/greeting으로 들어온 메시지를 처리하고 /topic/greetings로 브로드캐스트한다.
     *
     * <p>주의: @SendTo를 생략하면 기본 destination은 입력 destination에서
     * /app을 /topic으로 바꾼 것이 된다. 즉, /app/greeting → /topic/greeting.
     * 여기서는 명시적으로 /topic/greetings(복수형)를 지정했다.
     */
    @MessageMapping("/greeting")
    @SendTo("/topic/greetings")
    public String handle(String greeting) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "[" + timestamp + "] " + greeting;
    }
}