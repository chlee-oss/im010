package kr.co.im010.batch.schedule;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.batch.crawl.CrawlService;
import kr.co.im010.core.mapper.CrawlMapper;

/**
 * im010.crawl.run-now=mv,nt 로 시작하면 해당 제휴사를 즉시 수집하고 종료한다 (로컬 확인 · 수동 재수집용).
 * 백오피스의 [즉시 실행]은 crawl_job 에 MANUAL 작업을 넣고, 스케줄러가 처리한다.
 */
@Component
public class RunNowRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunNowRunner.class);

    private final CrawlProperties props;
    private final CrawlMapper crawlMapper;
    private final CrawlService crawlService;
    private final ConfigurableApplicationContext context;

    public RunNowRunner(CrawlProperties props, CrawlMapper crawlMapper, CrawlService crawlService,
                        ConfigurableApplicationContext context) {
        this.props = props;
        this.crawlMapper = crawlMapper;
        this.crawlService = crawlService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.runNow() == null || props.runNow().isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(props.zone());
        // attempt = maxAttempts: 수동 실행은 실패해도 재시도 작업을 남기지 않는다
        for (String partner : props.runNow()) {
            crawlMapper.insertJob(partner.trim(), "MANUAL", null, props.maxAttempts(), today, 0, "cli");
        }
        int done = crawlService.processQueue();
        log.info("즉시 수집 {}건 완료", done);
        System.exit(SpringApplication.exit(context));
    }
}
