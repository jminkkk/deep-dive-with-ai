package com.learning.websocket.stage5;

/**
 * Stage 5: 외부 브로커 설정 예시 (참고용, 실행되지 않는 코드).
 *
 * <p>이 클래스는 실제 @Configuration이 아니다.
 * 프로덕션에서 RabbitMQ 같은 외부 브로커를 연결할 때의 설정을 보여주는 참고 코드다.
 *
 * <p>외부 브로커 사용 시 아키텍처 변화:
 * <pre>
 * [SimpleBroker 아키텍처]
 * Client ↔ Spring Server (SimpleBrokerMessageHandler)
 *
 * [External Broker 아키텍처]
 * Client ↔ Spring Server ↔ RabbitMQ/ActiveMQ
 *                          (StompBrokerRelayMessageHandler)
 *
 * [다중 서버 아키텍처]
 * Client1 ↔ Server A ↔ RabbitMQ ↔ Server B ↔ Client2
 * </pre>
 *
 * <p>SimpleBroker → External Broker 전환 시 주의점:
 * <ul>
 *   <li>/topic과 /queue의 라우팅 방식이 실제로 달라진다
 *       (SimpleBroker에서는 차이 없음, RabbitMQ에서는 /topic=fanout, /queue=direct)</li>
 *   <li>"system" 연결(서버→브로커)과 "client" 연결(클라이언트별)이 별도로 관리된다</li>
 *   <li>브로커 연결 실패 시 BrokerAvailabilityEvent가 발행된다</li>
 *   <li>heartbeat 협상이 브로커와 서버 사이에서도 발생한다</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html">
 *      External Broker</a>
 */
public class ExternalBrokerConfigExample {

    /*
     * 실제 설정 코드 (참고용):
     *
     * @Configuration
     * @EnableWebSocketMessageBroker
     * public class ExternalBrokerConfig implements WebSocketMessageBrokerConfigurer {
     *
     *     @Override
     *     public void configureMessageBroker(MessageBrokerRegistry registry) {
     *         // enableSimpleBroker 대신 enableStompBrokerRelay 사용
     *         registry.enableStompBrokerRelay("/topic", "/queue")
     *                 .setRelayHost("localhost")        // RabbitMQ 호스트
     *                 .setRelayPort(61613)               // STOMP 포트 (AMQP 5672가 아님!)
     *                 .setSystemLogin("guest")           // 서버→브로커 연결용 계정
     *                 .setSystemPasscode("guest")
     *                 .setClientLogin("guest")           // 클라이언트→브로커 연결용 계정
     *                 .setClientPasscode("guest")
     *                 .setSystemHeartbeatSendInterval(10000)
     *                 .setSystemHeartbeatReceiveInterval(10000);
     *
     *         // 주의: 외부 브로커 사용 시 reactor-netty 의존성 필요
     *         // implementation 'io.projectreactor.netty:reactor-netty'
     *
     *         registry.setApplicationDestinationPrefixes("/app");
     *     }
     *
     *     @Override
     *     public void registerStompEndpoints(StompEndpointRegistry registry) {
     *         registry.addEndpoint("/ws");
     *     }
     * }
     */
}