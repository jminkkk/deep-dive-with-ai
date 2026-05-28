package com.learning.websocket.stage4;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Stage 4: 에러 핸들링 동작을 확인하기 위한 컨트롤러.
 */
@Controller
public class NotificationController {

    @MessageMapping("/notify.all")
    @SendTo("/topic/notifications")
    public String notifyAll(String message) {
        return "알림: " + message;
    }

    /**
     * 의도적으로 예외를 발생시켜 @MessageExceptionHandler 동작을 확인한다.
     */
    @MessageMapping("/notify.error")
    @SendToUser("/queue/result")
    public String triggerError(String message) {
        if ("error".equals(message)) {
            throw new IllegalArgumentException("의도적인 에러: '" + message + "'는 허용되지 않습니다");
        }
        return "처리 완료: " + message;
    }
}