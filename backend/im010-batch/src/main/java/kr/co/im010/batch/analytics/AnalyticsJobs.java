package kr.co.im010.batch.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import kr.co.im010.core.mapper.AnalyticsMapper;
import kr.co.im010.core.mapper.SettingsMapper;

/**
 * 대시보드 집계 (DS-01~03): 고객 화면 이벤트(analytics_event) → 일별 집계(stat_daily_*).
 * 실시간은 없다 — 기본 1시간마다 어제 · 오늘을 원본에서 다시 만들고(같은 날을 몇 번 돌려도 결과가 같다),
 * 매일 새벽 보관 기간(기본 90일)이 지난 원본을 지운다. 마지막 집계 시각은 admin_setting analytics_aggregated_at.
 */
@Component
@ConditionalOnProperty(prefix = "im010.analytics", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsJobs {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsJobs.class);

    private final AnalyticsMapper mapper;
    private final SettingsMapper settings;
    private final TransactionTemplate tx;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public AnalyticsJobs(AnalyticsMapper mapper, SettingsMapper settings, TransactionTemplate tx,
                         @Value("${im010.crawl.zone:Asia/Seoul}") ZoneId zone,
                         @Value("${im010.analytics.retention-days:90}") int retentionDays) {
        this(mapper, settings, tx, retentionDays, Clock.system(zone));
    }

    AnalyticsJobs(AnalyticsMapper mapper, SettingsMapper settings, TransactionTemplate tx, int retentionDays, Clock clock) {
        this.mapper = mapper;
        this.settings = settings;
        this.tx = tx;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    /** 어제 · 오늘 다시 집계 (자정 직후에도 어제 마지막 이벤트까지 반영) */
    @Scheduled(fixedDelayString = "${im010.analytics.interval:PT1H}", initialDelayString = "${im010.analytics.initial-delay:PT2M}")
    public void aggregateRecent() {
        LocalDate today = LocalDate.now(clock);
        try {
            aggregate(today.minusDays(1));
            aggregate(today);
            settings.updateSetting("analytics_aggregated_at", OffsetDateTime.now(clock).toString(), "batch");
        } catch (RuntimeException e) {
            log.error("대시보드 집계 실패", e);
        }
    }

    /** 원본 정리: 매일 03:30 */
    @Scheduled(cron = "${im010.analytics.purge-cron:0 30 3 * * *}", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void purge() {
        LocalDate before = LocalDate.now(clock).minusDays(retentionDays);
        int n = mapper.purgeEvents(before);
        if (n > 0) {
            log.info("통계 원본 정리: {} 이전 {}건", before, n);
        }
    }

    /** 하루치를 지우고 다시 만든다 (한 트랜잭션) */
    public void aggregate(LocalDate day) {
        tx.executeWithoutResult(s -> {
            mapper.deleteStats(day);
            mapper.aggregateVisit(day);
            mapper.aggregatePage(day);
            mapper.aggregatePlan(day);
            mapper.aggregateInterest(day);
            mapper.aggregateContent(day);
        });
        log.debug("대시보드 집계: {}", day);
    }
}
