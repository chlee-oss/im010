package kr.co.im010.admin.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
