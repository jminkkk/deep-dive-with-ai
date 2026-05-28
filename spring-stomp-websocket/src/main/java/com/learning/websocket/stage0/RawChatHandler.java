package com.learning.websocket.stage0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 0: Raw WebSocket 핸들러 — STOMP 없이 채팅을 구현한다.
 *
 * <p>이 클래스는 STOMP 프로토콜 없이 WebSocket만으로 채팅을 구현할 때
 * 발생하는 문제들을 직접 체험하기 위한 코드다.
 *
 * <p>문제점 목록:
 * <ul>
 *   <li>문제 1: 메시지 포맷을 직접 정의하고 파싱해야 한다 (JSON 수동 처리)</li>
 *   <li>문제 2: 메시지 타입별 라우팅을 switch/case로 직접 구현해야 한다</li>
 *   <li>문제 3: 브로드캐스트를 위해 세션 목록을 직접 관리해야 한다</li>
 *   <li>문제 4: 특정 사용자에게만 메시지를 보내는 기능이 없다</li>
 *   <li>문제 5: 에러 처리 포맷이 표준화되지 않는다</li>
 * </ul>
 */
public class RawChatHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 문제 1: 수동 JSON 파싱 — 포맷이 틀리면 서버에서 직접 에러 처리해야 한다
        Map<String, String> parsed;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = objectMapper.readValue(message.getPayload(), Map.class);
            parsed = map;
        } catch (JsonProcessingException e) {
            sendError(session, "INVALID_JSON", "JSON 파싱 실패: " + e.getMessage());
            return;
        }

        String type = parsed.get("type");
        if (type == null) {
            sendError(session, "MISSING_TYPE", "메시지에 'type' 필드가 없습니다");
            return;
        }

        // 문제 2: 수동 라우팅 — 메시지 타입이 늘어날수록 switch가 비대해진다
        switch (type) {
            case "CHAT" -> handleChat(parsed, session);
            case "DM" -> handleDirectMessage(parsed, session);
            case "JOIN" -> handleJoin(parsed, session);
            default -> sendError(session, "UNKNOWN_TYPE", "알 수 없는 메시지 타입: " + type);
        }
    }

    private void handleChat(Map<String, String> data, WebSocketSession sender) {
        String content = data.getOrDefault("content", "");
        String senderName = data.getOrDefault("sender", "anonymous");

        Map<String, String> response = Map.of(
                "type", "CHAT",
                "content", content,
                "sender", senderName
        );

        // 문제 3: 수동 브로드캐스트 — 세션 순회, 전송 실패 처리를 직접 해야 한다
        broadcast(response);
    }

    /**
     * 문제 4: 특정 사용자에게만 메시지 보내기(DM)가 매우 복잡하다.
     * 세션 ID나 사용자 식별자로 직접 매핑해야 하며,
     * 한 사용자가 여러 탭을 열면 어떤 세션에 보낼지 결정해야 한다.
     */
    private void handleDirectMessage(Map<String, String> data, WebSocketSession sender) {
        String targetSessionId = data.get("targetSessionId");
        if (targetSessionId == null) {
            sendError(sender, "MISSING_TARGET", "DM 대상 세션 ID가 없습니다");
            return;
        }

        // 세션 ID로 대상을 직접 찾아야 한다
        WebSocketSession target = sessions.stream()
                .filter(s -> s.getId().equals(targetSessionId))
                .findFirst()
                .orElse(null);

        if (target == null || !target.isOpen()) {
            sendError(sender, "TARGET_NOT_FOUND", "대상 세션을 찾을 수 없습니다");
            return;
        }

        Map<String, String> response = Map.of(
                "type", "DM",
                "content", data.getOrDefault("content", ""),
                "sender", data.getOrDefault("sender", "anonymous")
        );
        sendToSession(target, response);
    }

    private void handleJoin(Map<String, String> data, WebSocketSession session) {
        String name = data.getOrDefault("sender", "anonymous");
        Map<String, String> response = Map.of(
                "type", "SYSTEM",
                "content", name + " 님이 입장했습니다"
        );
        broadcast(response);
    }

    private void broadcast(Map<String, String> data) {
        sessions.forEach(s -> sendToSession(s, data));
    }

    private void sendToSession(WebSocketSession session, Map<String, String> data) {
        if (!session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(data);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            // 문제 5: 전송 실패 시 세션 정리 로직도 직접 구현해야 한다
            sessions.remove(session);
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        // 에러 포맷도 직접 정의 — 클라이언트와 약속이 필요하다
        sendToSession(session, Map.of(
                "type", "ERROR",
                "code", code,
                "message", message
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}