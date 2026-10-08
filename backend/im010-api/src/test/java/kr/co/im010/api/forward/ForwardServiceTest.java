package kr.co.im010.api.forward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import kr.co.im010.api.config.Im010Properties;
import kr.co.im010.api.web.NotFoundException;
import kr.co.im010.core.mapper.InternetProductMapper;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.ForwardLog;
import kr.co.im010.core.row.InternetForwardTarget;
import kr.co.im010.core.row.PlanForwardTarget;

class ForwardServiceTest {

    PlanMapper planMapper = mock(PlanMapper.class);
    InternetProductMapper internetMapper = mock(InternetProductMapper.class);
    ForwardLogWriter writer = mock(ForwardLogWriter.class);
    ForwardService service;

    @BeforeEach
    void setUp() {
        service = new ForwardService(planMapper, internetMapper, writer, new Im010Properties("https://www.im010.test/"));
    }

    private ForwardLog loggedEntry() {
        ArgumentCaptor<ForwardLog> captor = ArgumentCaptor.forClass(ForwardLog.class);
        verify(writer).write(captor.capture());
        return captor.getValue();
    }

    @Test
    void publishedPlanForwardsToActivationUrl() {
        when(planMapper.findForwardTarget(7)).thenReturn(new PlanForwardTarget(7, "mv", "POSTPAID", "PUBLISHED", "https://partner.test/plan", 70L));

        assertThat(service.forwardPlan(7, "S2")).hasToString("https://partner.test/plan");

        ForwardLog entry = loggedEntry();
        assertThat(entry.result()).isEqualTo("FORWARDED");
        assertThat(entry.kind()).isEqualTo("PLAN");
        assertThat(entry.partnerRef()).isEqualTo("mv");
        assertThat(entry.fromPage()).isEqualTo("S2");
    }

    @Test
    void endedPlanGoesBackToDetailWithNotice() {
        when(planMapper.findForwardTarget(3)).thenReturn(new PlanForwardTarget(3, "nt", "POSTPAID", "ENDED", "https://partner.test/plan", 70L));

        assertThat(service.forwardPlan(3, "S2")).hasToString("https://www.im010.test/plans/3?ended=1");
        assertThat(loggedEntry().result()).isEqualTo("ENDED");
    }

    @Test
    void publishedPlanWithoutUrlIsRecordedAsNoUrl() {
        when(planMapper.findForwardTarget(5)).thenReturn(new PlanForwardTarget(5, "im", "POSTPAID", "PUBLISHED", " ", 70L));

        assertThat(service.forwardPlan(5, null)).hasToString("https://www.im010.test/plans/5?forward=unavailable");
        ForwardLog entry = loggedEntry();
        assertThat(entry.result()).isEqualTo("NO_URL");
        assertThat(entry.fromPage()).isEqualTo("UNKNOWN");
    }

    @Test
    void hiddenOrPendingPlanGoesHome() {
        when(planMapper.findForwardTarget(9)).thenReturn(new PlanForwardTarget(9, "nt", "POSTPAID", "PENDING", "https://partner.test/plan", 70L));

        assertThat(service.forwardPlan(9, "S2")).hasToString("https://www.im010.test/");
        assertThat(loggedEntry().result()).isEqualTo("HIDDEN");
    }

    @Test
    void unknownPlanIsNotFound() {
        assertThatThrownBy(() -> service.forwardPlan(999, "S2")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void internetForwardsToApplyUrl() {
        when(internetMapper.findForwardTarget(1)).thenReturn(new InternetForwardTarget(1, true, 1L, "https://agency.test/apply"));

        assertThat(service.forwardInternet(1, "MAIN")).hasToString("https://agency.test/apply");
        ForwardLog entry = loggedEntry();
        assertThat(entry.kind()).isEqualTo("INTERNET");
        assertThat(entry.partnerRef()).isEqualTo("1");
        assertThat(entry.result()).isEqualTo("FORWARDED");
    }

    @Test
    void internetWithoutPartnerUrlReturnsToSection() {
        when(internetMapper.findForwardTarget(3)).thenReturn(new InternetForwardTarget(3, true, null, null));

        assertThat(service.forwardInternet(3, "MAIN")).hasToString("https://www.im010.test/?forward=unavailable#internet-compare");
        assertThat(loggedEntry().result()).isEqualTo("NO_URL");
    }

    @Test
    void fromPageIsSanitized() {
        assertThat(ForwardService.sanitizeFrom("S2")).isEqualTo("S2");
        assertThat(ForwardService.sanitizeFrom("<script>")).isEqualTo("UNKNOWN");
        assertThat(ForwardService.sanitizeFrom("x".repeat(31))).isEqualTo("UNKNOWN");
    }

    @Test
    void neverForwardsToUrlFromRequest() {
        when(planMapper.findForwardTarget(7)).thenReturn(new PlanForwardTarget(7, "mv", "POSTPAID", "PUBLISHED", "https://partner.test/plan", 70L));
        // from 파라미터에 URL을 넣어도 DB의 URL로만 이동
        assertThat(service.forwardPlan(7, "https://evil.test")).hasToString("https://partner.test/plan");
        verify(writer).write(any());
    }
}
