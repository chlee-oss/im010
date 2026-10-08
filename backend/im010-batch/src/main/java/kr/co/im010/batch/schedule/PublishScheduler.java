package kr.co.im010.batch.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import kr.co.im010.batch.notify.Notifier;
import kr.co.im010.batch.notify.Notifier.Level;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.row.PlanVersionRow;

/**
 * 게시 예약 실행 (PR-01): 1분마다 예약 시각이 지난 버전을 게시한다 — 게시 중 버전 교체 · 기준일(수집일) 갱신.
 * 필수 항목 확인은 예약할 때 백오피스가 했다. 실패하면 알림 (7장 "게시 예약 실패").
 */
@Component
@ConditionalOnProperty(prefix = "im010.crawl", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class PublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishScheduler.class);

    private final PlanAdminMapper planMapper;
    private final TransactionTemplate tx;
    private final Notifier notifier;

    public PublishScheduler(PlanAdminMapper planMapper, TransactionTemplate tx, Notifier notifier) {
        this.planMapper = planMapper;
        this.tx = tx;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${im010.crawl.poll-interval:PT1M}", initialDelayString = "PT20S")
    public void publishDue() {
        List<PlanVersionRow> due = planMapper.findDueVersions();
        for (PlanVersionRow v : due) {
            try {
                tx.executeWithoutResult(s -> {
                    planMapper.markVersionPublished(v.id(), "scheduler");
                    planMapper.pointToVersion(v.planId(), v.id());
                });
                log.info("예약 게시: plan {} v{} ({})", v.planId(), v.versionNo(), v.name());
            } catch (RuntimeException e) {
                log.error("예약 게시 실패: plan {} version {}", v.planId(), v.id(), e);
                notifier.send(AlertType.PUBLISH_FAILURE, Level.URGENT, "게시 예약 실패", "요금제 " + v.planId() + " · " + v.name() + " — " + e.getMessage());
            }
        }
    }
}
