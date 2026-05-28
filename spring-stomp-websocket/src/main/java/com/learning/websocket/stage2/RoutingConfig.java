package com.learning.websocket.stage2;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Stage 2: 메시지 라우팅 심화 설정.
 *
 * <p>Stage 1과 설정은 동일하지만, 이 Stage에서는 {@code @SendTo}와 {@code @SendToUser}의
 * 차이, {@code @SubscribeMapping}의 동작, destination prefix의 의미를 깊이 학습한다.
 *
 * <p>{@code /queue}는 관례상 1:1 메시지에 사용하고, {@code /topic}은 1:N 브로드캐스트에 사용한다.
 * 이것은 STOMP 스펙이 아닌 Spring의 관례이며, SimpleBroker에서는 기능 차이가 없다.
 * 외부 브로커(RabbitMQ 등)를 사용할 때 이 관례가 실제 라우팅 방식에 영향을 미친다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class RoutingConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        // /user prefix — @SendToUser가 사용하는 prefix (기본값: /user)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }
}