package kr.co.im010.api.analytics;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이용 통계 수집. 고객 화면은 세션마다 한 번 /api/events/config 로 수집 여부를 보고, 켜져 있을 때만 이벤트를 묶어 보낸다.
 * 보내는 쪽은 sendBeacon(text/plain) 을 쓰므로 본문 형식을 가리지 않고 JSON 으로 읽는다. 늘 204 (결과를 알려 주지 않는다).
 */
@RestController
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/api/events/config")
    public ResponseEntity<Map<String, Boolean>> config() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("enabled", service.enabled()));
    }

    @PostMapping(path = "/api/events", consumes = "*/*")
    public ResponseEntity<Void> collect(@RequestBody(required = false) String body,
                                        @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        service.collect(AnalyticsService.parse(body), userAgent);
        return ResponseEntity.noContent().build();
    }
}
