package kr.co.im010.batch.schedule;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.batch.crawl.CrawlService;
import kr.co.im010.core.mapper.CrawlItemMapper;
import kr.co.im010.core.mapper.CrawlMapper;

/**
 * 1분마다 BA-01 스케줄을 확인해 오늘 실행할 제휴사를 작업 큐에 넣고, 큐(스케줄 · 즉시 실행 · 재시도)를 처리한다.
 * 스케줄은 DB(crawl_schedule)에 있으므로 백오피스에서 바꾸면 다음 확인 때 반영된다.
 */
@Component
@ConditionalOnProperty(prefix = "im010.crawl", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);
    private static final int STALE_MINUTES = 120;

    private final CrawlMapper crawlMapper;
    private final CrawlItemMapper itemMapper;
    private final CrawlService crawlService;
    private final CrawlProperties props;

    public CrawlScheduler(CrawlMapper crawlMapper, CrawlItemMapper itemMapper, CrawlService crawlService, CrawlProperties props) {
        this.crawlMapper = crawlMapper;
        this.itemMapper = itemMapper;
        this.crawlService = crawlService;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int n = crawlMapper.requeueStaleJobs(STALE_MINUTES);
        if (n > 0) {
            log.warn("RUNNING 상태로 남은 수집 작업 {}건을 다시 대기열에 넣었습니다", n);
        }
    }

    @Scheduled(fixedDelayString = "${im010.crawl.poll-interval:PT1M}", initialDelayString = "PT10S")
    public void tick() {
        ZonedDateTime now = ZonedDateTime.now(props.zone());
        String dow = now.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
        int queued = crawlMapper.enqueueScheduled(now.toLocalDate(), dow, now.toLocalTime());
        if (queued > 0) {
            log.info("스케줄 수집 {}건 대기열 등록", queued);
        }
        crawlService.processQueue();
    }

    /** 수집 기록 보관 기간이 지난 처리 완료 건 삭제 (결정 #28). */
    @Scheduled(cron = "0 30 3 * * *", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void purge() {
        LocalDate before = LocalDate.now(props.zone()).minusDays(props.retentionDays());
        int n = itemMapper.deleteExpired(before);
        log.info("보관 기간 지난 수집 건 {}건 삭제 ({} 이전)", n, before);
    }
}
