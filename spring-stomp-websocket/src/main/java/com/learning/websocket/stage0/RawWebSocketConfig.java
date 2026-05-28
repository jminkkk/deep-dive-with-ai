package com.learning.websocket.stage0;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Stage 0: Raw WebSocket 설정.
 *
 * <p>STOMP 없이 순수 WebSocket만 사용한다.
 * 메시지 형식, 라우팅, 브로드캐스트를 모두 직접 구현해야 한다.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket.html">
 *      Spring WebSocket Support</a>
 */
@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rawChatHandler(), "/raw-ws");
    }

    @Bean
    public RawChatHandler rawChatHandler() {
        return new RawChatHandler();
    }
}