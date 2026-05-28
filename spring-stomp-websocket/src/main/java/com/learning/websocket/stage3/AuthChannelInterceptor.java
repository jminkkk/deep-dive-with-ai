package com.learning.websocket.stage3;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 3: STOMP 메시지 레벨 인증/인가 인터셉터.
 *
 * <p>이 인터셉터는 clientInboundChannel에 등록되어
 * STOMP 프레임이 처리되기 전에 보안 검사를 수행한다.
 *
 * <p>실무에서는 JWT 토큰 검증, Spring Security와의 통합 등
 * 더 정교한 인증 로직을 사용하지만, 교육 목적으로 단순화했다.
 *
 * <p>주의: 이 인터셉터에서 예외를 던지면 클라이언트에 STOMP ERROR 프레임이 전송되고
 * WebSocket 연결이 즉시 종료된다. STOMP 스펙에서 ERROR 프레임은 "치명적"이다.
 *
 * @see <a href="https://stomp.github.io/stomp-specification-1.2.html#ERROR">
 *      STOMP 1.2 Spec - ERROR Frame</a>
 */
@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    // 교육용 단순화: 실제로는 DB나 외부 인증 서버를 사용한다
    private static final Map<String, String> VALID_TOKENS = Map.of(
            "token-alice", "alice",
            "token-bob", "bob",
            "token-admin", "admin"
    );

    private static final Set<String> ADMIN_ONLY_DESTINATIONS = Set.of(
            "/topic/admin",
            "/app/admin"
    );

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SEND, SUBSCRIBE -> handleAuthorization(accessor);
            default -> { /* DISCONNECT 등은 인가 불필요 */ }
        }

        return message;
    }

    /**
     * CONNECT 시 인증 처리.
     *
     * <p>클라이언트가 STOMP CONNECT 프레임에 커스텀 헤더로 토큰을 전달한다.
     * 인증 성공 시 Principal을 설정하면, 이후 모든 STOMP 메시지에서
     * {@code Principal}을 메서드 파라미터로 받을 수 있다.
     *
     * <p>주의: STOMP 스펙의 login/passcode 헤더가 아닌 커스텀 헤더(Authorization)를 사용한다.
     * Spring은 기본적으로 login/passcode를 무시하기 때문이다.
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new SecurityException("인증 토큰이 없습니다. 'Authorization' 헤더를 포함해주세요.");
        }

        String token = authHeaders.get(0);
        String username = VALID_TOKENS.get(token);
        if (username == null) {
            throw new SecurityException("유효하지 않은 토큰입니다: " + token);
        }

        // Principal 설정 — 이후 @MessageMapping 메서드에서 Principal 파라미터로 접근 가능
        Principal principal = new UsernamePasswordAuthenticationToken(username, null, List.of());
        accessor.setUser(principal);
    }

    /**
     * SEND/SUBSCRIBE 시 인가(권한) 검사.
     *
     * <p>인증된 사용자가 특정 destination에 접근할 권한이 있는지 확인한다.
     * 예: /topic/admin은 admin 사용자만 구독 가능.
     */
    private void handleAuthorization(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        Principal user = accessor.getUser();

        // 인증되지 않은 사용자가 메시지를 보내려는 경우
        if (user == null) {
            throw new SecurityException("인증되지 않은 사용자입니다. 먼저 CONNECT하세요.");
        }

        // destination 레벨 인가
        if (ADMIN_ONLY_DESTINATIONS.stream().anyMatch(destination::startsWith)) {
            if (!"admin".equals(user.getName())) {
                throw new SecurityException(
                        "'" + destination + "'에 대한 접근 권한이 없습니다. (admin 전용)");
            }
        }
    }
}
