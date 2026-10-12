package kr.co.im010.batch.notify;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.batch.notify.Notifier.Level;
import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.row.CountRow;
import kr.co.im010.core.row.RunSummaryRow;
import kr.co.im010.core.row.StaleDataRow;

/**
 * 정기 확인 알림 (백오피스 7장): 하루 수집 결과 요약 · 점검 · 승인 지연 · 기준일 경과. 같은 알림은 하루 한 번.
 */
@Component
@ConditionalOnProperty(prefix = "im010.crawl", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AlertJobs {

    private static final Map<String, String> TYPE = Map.of("POSTPAID", "후불", "PREPAID", "선불", "MONTHLY", "이달의 요금제");
    private static final Map<String, String> RESULT = Map.of("SUCCESS", "성공", "FAILED", "실패", "ABNORMAL", "이상");

    private final NotifyMapper notifyMapper;
    private final Notifier notifier;
    private final CrawlProperties props;

    public AlertJobs(NotifyMapper notifyMapper, Notifier notifier, CrawlProperties props) {
        this.notifyMapper = notifyMapper;
        this.notifier = notifier;
        this.props = props;
    }

    /** 수집 결과 요약: 매일 09:00 (기본 수집 04:00 이후 재시도까지 끝난 뒤) */
    @Scheduled(cron = "${im010.notify.summary-cron:0 0 9 * * *}", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void crawlSummary() {
        LocalDate today = LocalDate.now(props.zone());
        String body = summaryText(notifyMapper.findRunSummary(today));
        if (body == null) {
            return;
        }
        boolean trouble = body.contains("실패") || body.contains("이상");
        notifier.sendOnce(AlertType.CRAWL_SUMMARY, trouble ? Level.WARN : Level.INFO, today + " 수집 결과", body,
                "CRAWL_SUMMARY:" + today);
    }

    /** 점검 대기 · 승인 대기 24시간 초과: 매시 확인, 하루 한 번 알림 */
    @Scheduled(cron = "0 5 * * * *", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void reviewDelay() {
        List<CountRow> delayed = notifyMapper.countDelayedItems(OffsetDateTime.now().minusHours(props.delayHours()));
        long pending = count(delayed, "REVIEW_PENDING");
        long requested = count(delayed, "APPROVAL_REQUESTED");
        if (pending + requested == 0) {
            return;
        }
        LocalDate today = LocalDate.now(props.zone());
        notifier.sendOnce(AlertType.REVIEW_DELAY, Level.WARN, props.delayHours() + "시간 넘게 처리되지 않은 건이 있습니다",
                "점검 대기 " + pending + "건 · 승인 대기 " + requested + "건 → 백오피스 요금제배치관리 · 승인관리",
                "REVIEW_DELAY:" + today);
    }

    /** 기준일 경과: 게시 중 요금제의 최근 수집일이 3일보다 오래된 제휴사 (매일 09:10) */
    @Scheduled(cron = "0 10 9 * * *", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void staleData() {
        LocalDate today = LocalDate.now(props.zone());
        String body = staleText(notifyMapper.findLastCollectedByPartner(), today, props.staleDays());
        if (body != null) {
            notifier.sendOnce(AlertType.STALE_DATA, Level.WARN, "제휴사 요금제 정보가 " + props.staleDays() + "일 넘게 갱신되지 않았습니다",
                    body, "STALE_DATA:" + today);
        }
    }

    /** 알림 · 발송 기록 정리 (기본 90일) */
    @Scheduled(cron = "0 40 3 * * *", zone = "${im010.crawl.zone:Asia/Seoul}")
    public void purge() {
        notifyMapper.deleteOlderThan(OffsetDateTime.now().minusDays(props.notificationKeepDays()));
    }

    /** 요약 문장. 실행이 없으면 null */
    static String summaryText(List<RunSummaryRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        return rows.stream().map(r -> "%s %s %s — %d건 (신규 %d · 변경 %d · 종료 %d)".formatted(r.partnerName(),
                TYPE.getOrDefault(r.urlType(), r.urlType()), RESULT.getOrDefault(r.result(), r.result()), r.collectedCount(),
                r.newCount(), r.changedCount(), r.endedCount())).collect(Collectors.joining("\n"));
    }

    /** 기준일 경과 문장. 해당 제휴사가 없으면 null */
    static String staleText(List<StaleDataRow> rows, LocalDate today, int staleDays) {
        List<String> stale = rows.stream()
                .filter(r -> r.lastCollectedOn() == null || r.lastCollectedOn().isBefore(today.minusDays(staleDays)))
                .map(r -> r.partnerName() + " (최근 수집 " + (r.lastCollectedOn() == null ? "없음" : r.lastCollectedOn()) + ")")
                .toList();
        return stale.isEmpty() ? null : String.join("\n", stale) + "\n→ 스케줄관리 실행 이력 · 제휴사관리 수집 URL을 확인해 주세요";
    }

    private static long count(List<CountRow> rows, String key) {
        return rows.stream().filter(r -> key.equals(r.key())).mapToLong(CountRow::count).sum();
    }
}
