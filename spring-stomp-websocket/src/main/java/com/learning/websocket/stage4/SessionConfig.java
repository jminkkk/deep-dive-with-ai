package com.learning.websocket.stage4;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Stage 4: 세션 관리 & 에러 처리 설정.
 *
 * <p>heartbeat 설정이 핵심이다. heartbeat가 없으면:
 * <ul>
 *   <li>네트워크 끊김을 감지하지 못한다 (TCP keepalive만으로는 부족)</li>
 *   <li>좀비 세션이 쌓인다 (연결은 끊겼지만 서버는 모르는 상태)</li>
 *   <li>메모리 누수 원인이 된다</li>
 * </ul>
 *
 * <p>STOMP heartbeat 형식: "sx,ry"
 * - sx: 서버가 보낼 수 있는 최소 간격 (ms). 0이면 보내지 않음.
 * - ry: 서버가 받고 싶은 간격 (ms). 0이면 안 받아도 됨.
 * 실제 간격은 MAX(서버 능력, 클라이언트 요구)로 협상된다.
 *
 * @see <a href="https://stomp.github.io/stomp-specification-1.2.html#Heart-beating">
 *      STOMP 1.2 Spec - Heart-beating</a>
 */
@Configuration
@EnableWebSocketMessageBroker
public class SessionConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 주의: heartbeat를 사용하려면 반드시 TaskScheduler를 설정해야 한다.
        // TaskScheduler 없이 heartbeat만 설정하면 IllegalArgumentException이 발생한다.
        TaskScheduler taskScheduler = heartbeatScheduler();

        registry.enableSimpleBroker("/topic", "/queue")
                // heartbeat: [서버→클라이언트 간격, 클라이언트→서버 간격] (ms)
                // 10초마다 heartbeat를 주고받는다
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(taskScheduler);

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    /**
     * heartbeat 전송을 위한 TaskScheduler.
     *
     * <p>SimpleBroker는 heartbeat를 전송하기 위해 주기적 태스크를 스케줄링해야 한다.
     * 이 스케줄러가 없으면 heartbeat 설정 시 IllegalArgumentException이 발생한다.
     */
    private TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}