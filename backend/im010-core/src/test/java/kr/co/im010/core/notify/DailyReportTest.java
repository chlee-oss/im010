package kr.co.im010.core.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.co.im010.core.notify.DailyReport.Health;
import kr.co.im010.core.notify.DailyReport.Report;
import kr.co.im010.core.row.CountRow;

class DailyReportTest {

    static CountRow c(String key, String label, long n) {
        return new CountRow(key, label, n);
    }

    static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    static final List<Health> UP = List.of(new Health("api", "UP"), new Health("admin", "UP"));
    static final List<CountRow> STATUS = List.of(
            c("PUBLISHED:POSTPAID", "", 60), c("PUBLISHED:PREPAID", "", 23), c("HIDDEN:POSTPAID", "", 4),
            c("UNPUBLISHED:PREPAID", "", 2), c("CHANGE_DRAFT:POSTPAID", "", 3), c("CHANGE_SCHEDULED:PREPAID", "", 1));

    @Test
    void rendersAllSectionsWithDiffFromPreviousReport() {
        Report r = DailyReport.render(TODAY, UP, STATUS, List.of(c("POSTPAID", "", 2)),
                List.of(c("PLAN", "FORWARDED", 40), c("PLAN", "NO_URL", 2), c("INTERNET", "FORWARDED", 5)),
                List.of(c("REVIEW_PENDING", "", 7), c("APPROVAL_REQUESTED", "", 1)),
                "{\"date\":\"2026-11-10\",\"POSTPAID\":62,\"PREPAID\":23}");

        assertThat(r.level()).isEqualTo("INFO");
        assertThat(r.title()).isEqualTo("11/11(수) im010 현황");
        assertThat(r.body())
                .contains("**서비스 상태** ✅ 정상 (api · admin · batch)")
                .contains("- 후불 게시 중 60건 (-2) · 비노출 4")
                .contains("- 선불 게시 중 23건 (±0) · 게시 전 2")
                .contains("- 게시하지 않은 변경: 대기 3건 · 예약 1건")
                .contains("- 어제 판매 종료 2건 (후불 2)")
                .contains("- 개통하기(요금제) 42건 (⚠ 이동 못 함 2건 — 접수관리 확인)")
                .contains("- 상담 신청(인터넷) 5건")
                .contains("**처리 대기** 점검 대기 7건 · 승인 대기 1건");
        assertThat(r.countsJson()).isEqualTo("{\"date\":\"2026-11-11\",\"POSTPAID\":60,\"PREPAID\":23}");
    }

    @Test
    void firstReportHasNoDiffAndDownServiceIsUrgent() {
        Report r = DailyReport.render(TODAY, List.of(new Health("api", "응답 없음"), new Health("admin", "UP")), STATUS,
                List.of(), List.of(), List.of(), "");

        assertThat(r.level()).isEqualTo("URGENT");
        assertThat(r.body()).contains("🚨 확인 필요 — api 응답 없음")
                .contains("- 후불 게시 중 60건 · 비노출 4").doesNotContain("(±")
                .contains("- 어제 판매 종료 0건").contains("- 개통하기(요금제) 0건");
    }

    @Test
    void brokenPreviousJsonIsIgnored() {
        assertThat(DailyReport.previous("{not json")).isEmpty();
        assertThat(DailyReport.previous("{\"POSTPAID\":5}")).containsEntry("POSTPAID", 5L).doesNotContainKey("PREPAID");
    }
}
