package kr.co.im010.admin.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.notify.DailyReport;
import kr.co.im010.core.notify.KakaoWorkClient;
import kr.co.im010.core.notify.WebhookSender;
import kr.co.im010.core.parse.JsoupPageFetcher;
import kr.co.im010.core.parse.PageFetcher;
import kr.co.im010.core.parse.RatePlanParser;

/** 수집 URL [테스트] 용 파서 · 페이지 받기 (배치와 같은 파서, 같은 사이트 요청 간격 3초). */
@Configuration
public class CrawlTestConfig {

    @Bean
    public PageFetcher pageFetcher(AdminProperties props) {
        return new JsoupPageFetcher(props.userAgent(), Duration.ofSeconds(3), Duration.ofSeconds(20));
    }

    @Bean
    public RatePlanParser ratePlanParser() {
        return new RatePlanParser();
    }

    /** 알림 채널 [테스트 발송] */
    @Bean
    public WebhookSender webhookSender() {
        return new WebhookSender();
    }

    /** 카카오워크 채널 [채팅방 만들기] · [테스트 발송] */
    @Bean
    public KakaoWorkClient kakaoWorkClient() {
        return new KakaoWorkClient();
    }

    /** 정기 현황 리포트 — 같은 서버의 api · admin 헬스 체크 주소 (im010.notify.health.api · .admin) */
    @Bean
    public DailyReport dailyReport(NotifyMapper notifyMapper, SettingsMapper settingsMapper,
                                   @Value("${im010.notify.health.api:http://127.0.0.1:8080/actuator/health}") String api,
                                   @Value("${im010.notify.health.admin:http://127.0.0.1:8081/actuator/health}") String admin) {
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("api", api);
        urls.put("admin", admin);
        return new DailyReport(notifyMapper, settingsMapper, urls);
    }
}
