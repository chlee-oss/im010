package kr.co.im010.admin.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.review.ReviewService.BulkResult;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.core.mapper.CrawlItemMapper;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.row.PlanAdminRow;

class PlanBulkStatusTest {

    PlanAdminMapper planMapper;
    CrawlItemMapper crawlMapper;
    AuditService audit;
    PlanAdminService service;

    /** 2026-11-10 00:30 KST (UTC 기준으로는 전날) — 종료일은 한국 날짜 */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    static PlanAdminRow plan(long id, String status, Long draftId, OffsetDateTime publishAt) {
        return new PlanAdminRow(id, "mv", "마블링", "POSTPAID", "c" + id, status, "https://x", 100L + id, draftId,
                "요금제 " + id, 10000, "KT", null, publishAt, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        planMapper = mock(PlanAdminMapper.class);
        crawlMapper = mock(CrawlItemMapper.class);
        audit = mock(AuditService.class);
        service = new PlanAdminService(planMapper, crawlMapper, audit, null, CLOCK);
    }

    @Test
    void hideSkipsEndedAndAlreadyHidden() {
        when(planMapper.findPlansByIds(any())).thenReturn(List.of(
                plan(1, "PUBLISHED", null, null), plan(2, "ENDED", null, null), plan(3, "HIDDEN", null, null)));

        BulkResult r = service.changeStatus(List.of(1L, 2L, 3L), "HIDDEN");

        assertThat(r.done()).containsExactly(1L);
        assertThat(r.skipped()).extracting(s -> s.id()).containsExactly(2L, 3L);
        verify(planMapper).setStatus(1L, "HIDDEN");
        verify(audit).action(eq(PlanAdminService.PROGRAM), eq("HIDE"), eq("plan [1]"), anyString());
    }

    @Test
    void visibleOnlyRestoresHidden() {
        when(planMapper.findPlansByIds(any())).thenReturn(List.of(plan(1, "HIDDEN", null, null), plan(2, "PUBLISHED", null, null)));

        BulkResult r = service.changeStatus(List.of(1L, 2L), "VISIBLE");

        assertThat(r.done()).containsExactly(1L);
        verify(planMapper).restoreStatus(1L);
        verify(planMapper, never()).restoreStatus(2L);
    }

    @Test
    void endUsesKoreanDateHidesPicksAndCancelsSchedule() {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-12T10:00:00+09:00");
        when(planMapper.findPlansByIds(any())).thenReturn(List.of(
                plan(1, "PUBLISHED", 900L, at), plan(2, "HIDDEN", null, null), plan(3, "ENDED", null, null)));

        BulkResult r = service.changeStatus(List.of(1L, 2L, 3L), "ENDED");

        assertThat(r.done()).containsExactly(1L, 2L);
        assertThat(r.skipped()).extracting(s -> s.id()).containsExactly(3L);
        verify(planMapper).cancelSchedule(900L);
        verify(crawlMapper).endPlans(List.of(1L, 2L), LocalDate.of(2026, 11, 10));
        verify(crawlMapper).hidePicksOfPlans(List.of(1L, 2L));
        verify(audit).action(eq(PlanAdminService.PROGRAM), eq("END"), eq("plan [1, 2]"), anyString());
    }

    @Test
    void nothingChangedWritesNoAudit() {
        when(planMapper.findPlansByIds(any())).thenReturn(List.of(plan(1, "ENDED", null, null)));

        BulkResult r = service.changeStatus(List.of(1L), "ENDED");

        assertThat(r.done()).isEmpty();
        verify(crawlMapper, never()).endPlans(any(), any());
        verify(audit, never()).action(anyString(), anyString(), anyString(), anyString());
        verify(planMapper, never()).setStatus(anyLong(), anyString());
    }

    @Test
    void stateFollowsCustomerExposureAndChangeIsSeparate() {
        OffsetDateTime at = OffsetDateTime.parse("2026-11-12T10:00:00+09:00");
        // 게시 중인데 수집으로 새 값이 들어온 경우: 상태는 게시 중(노출), 변경 대기
        PlanAdminRow liveWithDraft = plan(1, "PUBLISHED", 900L, null);
        assertThat(PlanAdminService.state(liveWithDraft)).isEqualTo("PUBLISHED");
        assertThat(PlanAdminService.change(liveWithDraft)).isEqualTo("DRAFT");
        // 게시 중 + 변경 예약
        assertThat(PlanAdminService.change(plan(2, "PUBLISHED", 901L, at))).isEqualTo("SCHEDULED");
        // 한 번도 게시 안 된 신규 (예약 포함) → 게시 전
        assertThat(PlanAdminService.state(plan(3, "PENDING", 902L, null))).isEqualTo("UNPUBLISHED");
        assertThat(PlanAdminService.state(plan(4, "SCHEDULED", 903L, at))).isEqualTo("UNPUBLISHED");
        assertThat(PlanAdminService.change(plan(4, "SCHEDULED", 903L, at))).isEqualTo("SCHEDULED");
        // 비노출 · 판매 종료는 그대로, 판매 종료에는 변경 표시 없음
        assertThat(PlanAdminService.state(plan(5, "HIDDEN", 904L, null))).isEqualTo("HIDDEN");
        assertThat(PlanAdminService.change(plan(5, "HIDDEN", 904L, null))).isEqualTo("DRAFT");
        assertThat(PlanAdminService.state(plan(6, "ENDED", 905L, null))).isEqualTo("ENDED");
        assertThat(PlanAdminService.change(plan(6, "ENDED", 905L, null))).isNull();
        // 변경 없음
        assertThat(PlanAdminService.change(plan(7, "PUBLISHED", null, null))).isNull();
    }

    @Test
    void unknownTargetIsRejected() {
        assertThatThrownBy(() -> service.changeStatus(List.of(1L), "PUBLISHED")).isInstanceOf(ApiException.class);
    }
}
