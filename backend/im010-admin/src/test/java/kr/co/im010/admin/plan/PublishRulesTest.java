package kr.co.im010.admin.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import kr.co.im010.core.row.PlanVersionRow;

class PublishRulesTest {

    private static PlanVersionRow version(String dataText, BigDecimal gb, Integer monthly, Integer charge, Integer validDays,
                                          Integer discountMonths, Integer after) {
        return new PlanVersionRow(1, 1, 1, "요금제", dataText, gb, null, "기본 제공", "기본 제공", "LGU", "LTE",
                monthly, charge, validDays, discountMonths, after, "", "", "", LocalDate.of(2026, 10, 8),
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void 후불_필수_항목() {
        String url = "https://partner.example.com/rateplan_view.do?no=1";
        assertThat(PublishRules.missing("POSTPAID", version("데이터 7GB", new BigDecimal("7"), 9900, null, null, null, null), url))
                .isEmpty();
        assertThat(PublishRules.missing("POSTPAID", version("데이터 무제한", null, 9900, null, null, null, null), url))
                .isEmpty();
        assertThat(PublishRules.missing("POSTPAID", version("데이터 매일 5GB", null, 9900, null, null, 7, null), null))
                .containsExactly("데이터 기본 제공량", "할인 기간 · 할인 후 요금", "개통 URL");
    }

    @Test
    void 선불은_충전_금액과_사용_기간() {
        String url = "https://partner.example.com/x";
        assertThat(PublishRules.missing("PREPAID", version("데이터 3GB", new BigDecimal("3"), null, 11000, null, null, null), url))
                .containsExactly("사용 기간");
        assertThat(PublishRules.missing("PREPAID", version("데이터 3GB", new BigDecimal("3"), null, 11000, 30, null, null), url))
                .isEmpty();
    }
}
