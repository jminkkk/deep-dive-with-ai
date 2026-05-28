package com.learning.websocket.stage4;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stage 4: SimpMessagingTemplate — 서버가 먼저 메시지를 보내는 패턴.
 *
 * <p>@MessageMapping과의 핵심 차이:
 * <pre>
 * [@MessageMapping 패턴 — 클라이언트가 시작]
 * Client SEND → @MessageMapping → @SendTo → Broker → Subscribers
 *
 * [SimpMessagingTemplate 패턴 — 서버가 시작]
 * 이벤트 발생 → SimpMessagingTemplate.convertAndSend() → Broker → Subscribers
 * </pre>
 *
 * <p>SimpMessagingTemplate은 Spring Bean이므로 어디서든 주입받아 사용할 수 있다:
 * REST 컨트롤러, @Service, @Scheduled, @EventListener 등.
 *
 * <p>실무 사용 예:
 * <ul>
 *   <li>주문 상태 변경 → 해당 사용자에게 실시간 알림</li>
 *   <li>관리자 전체 공지 → 모든 접속자에게 브로드캐스트</li>
 *   <li>배치 작업 완료 → 대시보드 자동 갱신</li>
 * </ul>
 *
 * <p>주의: convertAndSend()의 payload도 MessageConverter를 거친다.
 * String을 보내면 text/plain, Map/POJO를 보내면 application/json이 된다.
 * Stage 2의 MessageConverter 함정과 동일한 규칙이 적용된다.
 *
 * @see SimpMessagingTemplate#convertAndSend(String, Object)
 * @see SimpMessagingTemplate#convertAndSendToUser(String, String, Object)
 */
@RestController
public class PushController {

    private final SimpMessagingTemplate messagingTemplate;

    public PushController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * REST API → WebSocket 브로드캐스트.
     *
     * <p>convertAndSend: 지정한 destination의 모든 구독자에게 메시지를 전송한다.
     * @SendTo와 동일한 결과이지만, WebSocket 메시지 수신 없이도 동작한다.
     */
    @PostMapping("/api/notifications")
    public String broadcast(@RequestBody String message) {
        messagingTemplate.convertAndSend("/topic/notifications", "알림: " + message);
        return "전송 완료";
    }

    /*
     * convertAndSendToUser 참고:
     *
     * messagingTemplate.convertAndSendToUser("alice", "/queue/private", "개인 알림");
     *
     * 내부 동작: /queue/private → /user/alice/queue/private 로 변환
     * 주의: STOMP CONNECT 시 Principal이 설정된 세션만 수신 가능하다.
     * (Stage 3의 AuthChannelInterceptor 참고)
     */
}