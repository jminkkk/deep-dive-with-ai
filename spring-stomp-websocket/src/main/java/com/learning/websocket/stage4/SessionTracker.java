package com.learning.websocket.stage4;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 4: WebSocket 세션 생명주기 추적.
 *
 * <p>Spring은 STOMP 세션의 각 단계에서 ApplicationEvent를 발행한다.
 * 이 이벤트들을 활용해 온라인 사용자 추적, 리소스 정리, 접속 로그 등을 구현한다.
 *
 * <p>이벤트 발생 순서:
 * <pre>
 * 1. SessionConnectEvent    — STOMP CONNECT 프레임 수신 시 (인증 전)
 * 2. SessionConnectedEvent  — STOMP CONNECTED 프레임 전송 후 (연결 완료)
 * 3. SessionSubscribeEvent  — STOMP SUBSCRIBE 프레임 수신 시
 * 4. SessionDisconnectEvent — STOMP DISCONNECT 또는 WebSocket 종료 시
 * </pre>
 *
 * <p>주의: SessionDisconnectEvent는 같은 세션에 대해 여러 번 발생할 수 있다.
 * 따라서 정리 로직은 반드시 멱등(idempotent)해야 한다.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/websocket/stomp/application-context-events.html">
 *      Events</a>
 */
@Component
public class SessionTracker {

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    /**
     * STOMP CONNECT 수신 — 아직 연결이 완료되지 않은 상태.
     * 여기서 수집한 정보(커스텀 헤더 등)는 로깅이나 감사(audit)에 활용한다.
     */
    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        // CONNECT 시점의 정보를 기록 (아직 연결 완료 전)
        activeSessions.add(sessionId);
    }

    /**
     * STOMP CONNECTED 전송 완료 — 연결이 확정된 시점.
     * 이 시점부터 클라이언트에게 메시지를 보낼 수 있다.
     */
    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String username = (accessor.getUser() != null) ? accessor.getUser().getName() : "anonymous";
        sessionUsers.put(sessionId, username);
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        // 구독 정보를 추적할 수 있다 (어떤 세션이 어떤 destination을 구독하는지)
    }

    /**
     * STOMP DISCONNECT 또는 WebSocket 종료.
     *
     * <p>주의: 이 이벤트는 같은 세션에 대해 여러 번 호출될 수 있다!
     * - 클라이언트가 DISCONNECT 프레임을 보내고 WebSocket도 닫히면 2번 발생
     * - 네트워크 끊김 시에도 발생
     *
     * <p>따라서 ConcurrentHashMap.remove()처럼 멱등 연산을 사용해야 한다.
     * if-contains-then-remove 같은 check-then-act 패턴은 race condition 위험이 있다.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        // 멱등 연산: 이미 제거되었더라도 에러가 발생하지 않는다
        activeSessions.remove(sessionId);
        sessionUsers.remove(sessionId);
    }

    public Set<String> getActiveSessions() {
        return Collections.unmodifiableSet(activeSessions);
    }

    public Map<String, String> getSessionUsers() {
        return Collections.unmodifiableMap(sessionUsers);
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}