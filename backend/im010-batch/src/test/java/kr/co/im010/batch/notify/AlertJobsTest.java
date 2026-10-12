package kr.co.im010.batch.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.co.im010.core.row.RunSummaryRow;
import kr.co.im010.core.row.StaleDataRow;

class AlertJobsTest {

    @Test
    void 수집_결과_요약_문장() {
        String text = AlertJobs.summaryText(List.of(
                new RunSummaryRow("마블링", "POSTPAID", "SUCCESS", 32, 1, 2, 0),
                new RunSummaryRow("위너스텔", "PREPAID", "FAILED", 0, 0, 0, 0)));
        assertThat(text).isEqualTo("마블링 후불 성공 — 32건 (신규 1 · 변경 2 · 종료 0)\n위너스텔 선불 실패 — 0건 (신규 0 · 변경 0 · 종료 0)");
        assertThat(AlertJobs.summaryText(List.of())).isNull();
    }

    @Test
    void 기준일_경과는_3일보다_오래된_제휴사만() {
        LocalDate today = LocalDate.of(2026, 10, 8);
        String text = AlertJobs.staleText(List.of(
                new StaleDataRow("mv", "마블링", LocalDate.of(2026, 10, 7)),
                new StaleDataRow("nt", "토리모바일", LocalDate.of(2026, 10, 5)),
                new StaleDataRow("id", "위너스텔", LocalDate.of(2026, 10, 4)),
                new StaleDataRow("sw", "시월모바일", null)), today, 3);
        assertThat(text).startsWith("위너스텔 (최근 수집 2026-10-04)\n시월모바일 (최근 수집 없음)");
        assertThat(AlertJobs.staleText(List.of(new StaleDataRow("mv", "마블링", today)), today, 3)).isNull();
    }
}
