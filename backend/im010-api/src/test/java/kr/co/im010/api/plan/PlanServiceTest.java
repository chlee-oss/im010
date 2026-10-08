package kr.co.im010.api.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.co.im010.api.web.NotFoundException;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.PlanRow;

class PlanServiceTest {

    PlanMapper mapper = mock(PlanMapper.class);
    PlanService service = new PlanService(mapper);

    static PlanRow row(long id, String type, String status, BigDecimal gb, String network, String tags) {
        return new PlanRow(id, "mv", "마블링", type, status, "넉넉 11GB+", "데이터 11GB + 매일 2GB", gb, "1Mbps",
                "기본 제공", "기본 제공", network, "5G", "POSTPAID".equals(type) ? 16500 : null,
                "PREPAID".equals(type) ? 6600 : null, null, null, null, tags, LocalDate.of(2026, 10, 1), null);
    }

    @Test
    void summarySplitsTagsAndLabelsNetwork() {
        PlanSummary s = PlanSummary.from(row(7, "POSTPAID", "PUBLISHED", new BigDecimal("11"), "LGU", "LGU+망|5G|유심비 무료"));
        assertThat(s.tags()).containsExactly("LGU+망", "5G", "유심비 무료");
        assertThat(s.networkLabel()).isEqualTo("LGU+");
        assertThat(s.unlimited()).isFalse();
        assertThat(PlanSummary.from(row(1, "POSTPAID", "PUBLISHED", null, "SKT", "")).unlimited()).isTrue();
        assertThat(PlanSummary.from(row(1, "POSTPAID", "PUBLISHED", null, "SKT", null)).tags()).isEmpty();
    }

    @Test
    void cheapestRejectsNonPositiveData() {
        assertThatThrownBy(() -> service.findCheapest(Network.SKT, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cheapestPassesNullForUnlimited() {
        when(mapper.findCheapestPostpaid("KT", null)).thenReturn(row(11, "POSTPAID", "PUBLISHED", null, "KT", ""));
        assertThat(service.findCheapest(Network.KT, null)).get().extracting(PlanSummary::id).isEqualTo(11L);
    }

    @Test
    void detailOfPrepaidHasNoSimilarPlans() {
        when(mapper.findDetail(13)).thenReturn(row(13, "PREPAID", "PUBLISHED", BigDecimal.ONE, "LGU", ""));
        PlanDetail d = service.findDetail(13);
        assertThat(d.similar()).isEmpty();
        assertThat(d.activatePath()).isEqualTo("/go/13?from=S2");
        verify(mapper, never()).findSimilarPostpaid(anyLong(), any(), any(), anyInt());
    }

    @Test
    void detailOfEndedPostpaidIsFlagged() {
        when(mapper.findDetail(7)).thenReturn(row(7, "POSTPAID", "ENDED", new BigDecimal("11"), "SKT", ""));
        when(mapper.findSimilarPostpaid(7, "SKT", new BigDecimal("11"), 3)).thenReturn(List.of(row(8, "POSTPAID", "PUBLISHED", new BigDecimal("15"), "SKT", "")));
        PlanDetail d = service.findDetail(7);
        assertThat(d.ended()).isTrue();
        assertThat(d.similar()).hasSize(1);
        assertThat(d.basisDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void unknownDetailIsNotFound() {
        assertThatThrownBy(() -> service.findDetail(404)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void planTypeIsValidated() {
        assertThat(PlanService.normalizeType(null)).isEqualTo("POSTPAID");
        assertThat(PlanService.normalizeType("prepaid")).isEqualTo("PREPAID");
        assertThatThrownBy(() -> PlanService.normalizeType("BOTH")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void networkParsing() {
        assertThat(Network.parse("lgu+")).isEqualTo(Network.LGU);
        assertThat(Network.parse(" skt ")).isEqualTo(Network.SKT);
        assertThatThrownBy(() -> Network.parse("SK")).isInstanceOf(IllegalArgumentException.class);
    }
}
