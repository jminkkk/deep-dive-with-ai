package com.learning.websocket.stage4;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;


/**
 * Stage 4: STOMP 전역 예외 처리.
 *
 * <p>@ControllerAdvice + @MessageExceptionHandler 조합으로
 * 모든 @MessageMapping 메서드에서 발생하는 예외를 한 곳에서 처리한다.
 *
 * <p>주의: 이 핸들러는 @MessageMapping 메서드 내부 예외만 처리한다.
 * ChannelInterceptor에서 발생한 예외는 여기서 잡히지 않는다.
 * ChannelInterceptor 예외는 STOMP ERROR 프레임으로 직접 전송되고 연결이 끊긴다.
 *
 * <p>차이점 정리:
 * <ul>
 *   <li>@MessageExceptionHandler — @MessageMapping 내부 예외 → 클라이언트에 메시지로 전달</li>
 *   <li>ChannelInterceptor 예외 — STOMP ERROR 프레임 → 연결 종료</li>
 *   <li>WebSocket 레벨 에러 — onTransportError → 복구 불가</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalStompExceptionHandler {

    /**
     * broadcast=false: 에러를 발생시킨 세션에만 전달.
     * 같은 사용자의 다른 탭/세션에는 에러가 전파되지 않는다.
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public String handleException(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "알 수 없는 오류";
        return "[" + ex.getClass().getSimpleName() + "] " + message;
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public String handleIllegalArgument(IllegalArgumentException ex) {
        return "[INVALID_ARGUMENT] " + ex.getMessage();
    }
}