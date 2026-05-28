package com.learning.websocket.stage5;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * Stage 5: 스케일링 테스트용 간단한 에코 컨트롤러.
 */
@Controller
public class EchoController {

    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public String echo(String message) {
        return "echo: " + message;
    }

    /**
     * I/O 바운드 작업 시뮬레이션.
     * 스레드 풀 설정이 성능에 미치는 영향을 확인하기 위한 엔드포인트.
     */
    @MessageMapping("/slow")
    @SendTo("/topic/slow")
    public String slow(String message) throws InterruptedException {
        Thread.sleep(100);
        return "slow: " + message;
    }
}