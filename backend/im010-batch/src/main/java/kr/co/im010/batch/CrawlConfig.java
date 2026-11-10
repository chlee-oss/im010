package kr.co.im010.batch;

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

@Configuration
public class CrawlConfig {

    @Bean
    public PageFetcher pageFetcher(CrawlProperties props) {
        return new JsoupPageFetcher(props.userAgent(), props.requestDelay(), props.timeout());
    }

    @Bean
    public RatePlanParser ratePlanParser() {
        return new RatePlanParser();
    }

    @Bean
    public WebhookSender webhookSender() {
        return new WebhookSender();
    }

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
