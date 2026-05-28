package com.learning.websocket.stage5;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Stage 5: 스케일링 & 성능 — 프로덕션을 위한 설정.
 *
 * <p>SimpleBroker의 한계:
 * <ul>
 *   <li>단일 JVM 메모리에 구독 정보를 저장 → 서버 재시작 시 구독 정보 소실</li>
 *   <li>서버 인스턴스 간 메시지 공유 불가 → 수평 확장(scale-out) 불가능</li>
 *   <li>메시지 영속성 없음 → 서버 다운 시 미전달 메시지 소실</li>
 * </ul>
 *
 * <p>해결책: 외부 메시지 브로커(RabbitMQ, ActiveMQ) 사용.
 * 이 설정에서는 SimpleBroker를 사용하되, 성능 관련 설정을 다룬다.
 * 외부 브로커 설정은 {@link ExternalBrokerConfigExample}에서 별도로 보여준다.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html">
 *      Performance</a>
 */
@Configuration
@EnableWebSocketMessageBroker
public class ScalableStompConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-");
        taskScheduler.initialize();

        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(taskScheduler);

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    /**
     * WebSocket 전송 계층 성능 설정.
     *
     * <p>주의해야 할 설정들:
     *
     * <p><b>sendTimeLimit</b>: 클라이언트에게 메시지를 보내는 최대 시간.
     * 느린 클라이언트가 전송을 블로킹하는 것을 방지한다.
     * 초과 시 WebSocket 세션이 강제 종료된다.
     *
     * <p><b>sendBufferSizeLimit</b>: 클라이언트에게 보내기 위해 버퍼링할 수 있는 최대 크기.
     * 느린 클라이언트 때문에 서버 메모리가 고갈되는 것을 방지한다.
     *
     * <p><b>messageSizeLimit</b>: 수신 가능한 STOMP 메시지 최대 크기.
     * STOMP 클라이언트는 큰 메시지를 16KB 단위로 분할 전송하고,
     * 서버가 이를 재조립한다. 이 값은 재조립 후의 최종 크기 제한이다.
     * 기본값: Tomcat 8KB, Jetty 64KB.
     *
     * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html">
     *      Performance Configuration</a>
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(15 * 1000)       // 15초 — 이 시간 안에 전송 못하면 세션 종료
                .setSendBufferSizeLimit(512 * 1024) // 512KB — 클라이언트당 버퍼 제한
                .setMessageSizeLimit(128 * 1024);   // 128KB — 수신 메시지 최대 크기
    }
}