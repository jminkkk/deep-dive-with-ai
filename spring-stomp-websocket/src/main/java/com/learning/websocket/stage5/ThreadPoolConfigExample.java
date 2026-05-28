package com.learning.websocket.stage5;

/**
 * Stage 5: 스레드 풀 설정 주의사항 (참고용, 실행되지 않는 코드).
 *
 * <p>Spring WebSocket은 3개의 주요 채널을 사용하며, 각각 별도의 스레드 풀을 가진다:
 *
 * <pre>
 * [clientInboundChannel]  — 클라이언트 → 서버 메시지 처리
 *     기본값: corePoolSize = 2 * 프로세서 수
 *
 * [clientOutboundChannel] — 서버 → 클라이언트 메시지 전송
 *     기본값: corePoolSize = 2 * 프로세서 수
 *
 * [brokerChannel]         — 서버 내부 → 브로커 메시지 전달
 *     기본값: corePoolSize = 2 * 프로세서 수
 * </pre>
 *
 * <p>주의: ThreadPoolExecutor의 함정
 * <pre>
 * 설정: corePoolSize=10, maxPoolSize=20, queueCapacity=Integer.MAX_VALUE(기본값)
 *
 * 기대: 10개 스레드가 바쁘면 최대 20개까지 늘어난다
 * 실제: 절대 20개로 늘어나지 않는다!
 *
 * 이유: ThreadPoolExecutor는 큐가 가득 찬 후에만 maxPoolSize까지 스레드를 생성한다.
 *       큐 용량이 Integer.MAX_VALUE이므로 큐가 절대 가득 차지 않는다.
 *       → maxPoolSize=20은 의미 없는 설정이 된다.
 *
 * 해결: queueCapacity를 적절히 줄여야 maxPoolSize가 효과를 발휘한다.
 * </pre>
 *
 * <p>설정 기준:
 * <ul>
 *   <li>CPU 바운드 작업 (JSON 파싱, 비즈니스 로직) → 프로세서 수에 맞춤</li>
 *   <li>I/O 바운드 작업 (DB 호출, 외부 API) → 프로세서 수보다 높게</li>
 *   <li>느린 클라이언트 대응 (Outbound) → 네트워크 속도에 따라 조정</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html">
 *      Performance Configuration</a>
 */
public class ThreadPoolConfigExample {

    /*
     * 실제 설정 코드 (참고용):
     *
     * @Override
     * public void configureClientInboundChannel(ChannelRegistration registration) {
     *     registration.taskExecutor()
     *             .corePoolSize(4)       // 기본 스레드 수
     *             .maxPoolSize(8)        // 최대 스레드 수
     *             .queueCapacity(100)    // 핵심: 큐 용량을 제한해야 maxPoolSize가 동작!
     *             .keepAliveSeconds(60); // 유휴 스레드 유지 시간
     * }
     *
     * @Override
     * public void configureClientOutboundChannel(ChannelRegistration registration) {
     *     registration.taskExecutor()
     *             .corePoolSize(4)
     *             .maxPoolSize(10)       // 느린 클라이언트 대응: Inbound보다 높게
     *             .queueCapacity(50);
     * }
     */
}