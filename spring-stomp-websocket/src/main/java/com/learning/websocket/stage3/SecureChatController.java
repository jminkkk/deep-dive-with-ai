package com.learning.websocket.stage3;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Stage 3: 인증이 적용된 채팅 컨트롤러.
 *
 * <p>AuthChannelInterceptor에서 설정한 Principal을
 * 메서드 파라미터로 자동 주입받는다.
 */
@Controller
public class SecureChatController {

    /**
     * 인증된 사용자만 메시지를 보낼 수 있다.
     * Principal은 STOMP CONNECT 시점에 AuthChannelInterceptor가 설정한 것이다.
     */
    @MessageMapping("/secure.chat")
    @SendTo("/topic/secure")
    public String secureChat(String message, Principal principal) {
        return principal.getName() + ": " + message;
    }

    /**
     * 관리자 전용 채널.
     * AuthChannelInterceptor에서 /app/admin destination 접근을 admin만 허용한다.
     */
    @MessageMapping("/admin.broadcast")
    @SendTo("/topic/admin")
    public String adminBroadcast(String message, Principal principal) {
        return "[ADMIN] " + principal.getName() + ": " + message;
    }

    @MessageMapping("/secure.whoami")
    @SendToUser("/queue/identity")
    public String whoAmI(Principal principal) {
        return "당신은 '" + principal.getName() + "'으로 인증되었습니다.";
    }
}