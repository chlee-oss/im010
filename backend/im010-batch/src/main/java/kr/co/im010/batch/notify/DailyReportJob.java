package kr.co.im010.batch.notify;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.batch.notify.Notifier.Level;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.notify.DailyReport;

/**
 * 정기 현황 리포트: 매일 08:00 (알림 종류 "정기 현황 리포트"를 받는 채널 · 메일로). 같은 날 한 번만.
 * 보낸 뒤 오늘 숫자를 남겨 다음 날 리포트의 전날 대비 증감에 쓴다.
 */
@Component
@ConditionalOnProperty(prefix = "im010.crawl", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DailyReportJob {

    private final DailyReport report;
    private final Notifier notifier;
    private final CrawlProperties props;

    public DailyReportJob(DailyReport report, Notifier notifier, CrawlProperties props) {
        this.report = report;
        this.notifier = notifier;
        this.props = props;
    }

    @Scheduled(cron = "${im010.notify.report-cron:0 0 8 * * *}", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void send() {
        LocalDate today = LocalDate.now(props.zone());
        DailyReport.Report r = report.build(today, props.zone());
        notifier.sendOnce(AlertType.DAILY_REPORT, Level.valueOf(r.level()), r.title(), r.body(), "DAILY_REPORT:" + today);
        report.save(r);
    }
}
