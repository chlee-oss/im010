package kr.co.im010.batch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
