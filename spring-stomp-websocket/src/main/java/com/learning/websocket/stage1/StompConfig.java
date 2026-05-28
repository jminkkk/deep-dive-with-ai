package com.learning.websocket.stage1;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Stage 1: STOMP 프로토콜 도입 — 구조화된 메시징.
 *
 * <p>Stage 0의 수동 라우팅/파싱/브로드캐스트를 STOMP가 모두 대체한다.
 *
 * <p>핵심 설정:
 * <ul>
 *   <li>{@code /app} — 클라이언트가 서버로 메시지를 보낼 때 사용하는 prefix.
 *       이 prefix로 시작하는 destination은 {@code @MessageMapping} 메서드로 라우팅된다.</li>
 *   <li>{@code /topic} — 메시지 브로커가 구독자에게 메시지를 배포할 때 사용하는 prefix.
 *       클라이언트는 이 prefix로 시작하는 destination을 SUBSCRIBE한다.</li>
 *   <li>{@code /ws} — WebSocket 핸드셰이크 엔드포인트. HTTP에서 WebSocket으로 업그레이드되는 진입점.</li>
 * </ul>
 *
 * <p>주의: /app prefix는 @MessageMapping의 destination 앞에 자동으로 붙는다.
 * 예: @MessageMapping("/greeting") → 클라이언트는 /app/greeting으로 SEND해야 한다.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html">
 *      Enable STOMP</a>
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 브로커가 관리하는 destination prefix — 클라이언트가 구독(SUBSCRIBE)하는 경로
        registry.enableSimpleBroker("/topic", "/queue");

        // 서버 애플리케이션으로 향하는 destination prefix — @MessageMapping으로 라우팅
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 핸드셰이크 엔드포인트
        registry.addEndpoint("/ws");
    }
}