package com.learning.websocket.stage3;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Stage 3: 인증/인가 — 보안이 적용된 STOMP 설정.
 *
 * <p>핵심 포인트: Spring WebSocket에서 인증은 두 시점에 발생할 수 있다.
 * <ol>
 *   <li>HTTP 핸드셰이크 시점 — Spring Security의 HTTP 보안 필터가 처리</li>
 *   <li>STOMP CONNECT 시점 — ChannelInterceptor에서 처리</li>
 * </ol>
 *
 * <p>주의: STOMP 프로토콜의 {@code login}/{@code passcode} 헤더는
 * 원래 STOMP-over-TCP용으로 설계된 것이며,
 * Spring은 WebSocket 환경에서 이 헤더를 기본적으로 무시한다.
 * 대신 HTTP 세션의 인증 정보를 WebSocket 세션에 자동으로 연결한다.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication.html">
 *      Authentication</a>
 */
@Configuration
@EnableWebSocketMessageBroker
public class SecureStompConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authChannelInterceptor;

    public SecureStompConfig(AuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    /**
     * 인바운드 채널에 인터셉터를 등록한다.
     *
     * <p>clientInboundChannel로 들어오는 모든 STOMP 메시지를 가로채서
     * CONNECT 시점에 인증을 수행하고, SEND/SUBSCRIBE 시점에 인가를 검사한다.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}