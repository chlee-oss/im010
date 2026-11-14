package kr.co.im010.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import kr.co.im010.admin.dashboard.DashboardService.KeyMetrics;
import kr.co.im010.admin.dashboard.DashboardService.TypeStat;
import kr.co.im010.core.row.AnalyticsRows.PageStat;
import kr.co.im010.core.row.AnalyticsRows.PlanStat;
import kr.co.im010.core.row.AnalyticsRows.Visit;

class DashboardKeyMetricsTest {

    private static PlanStat plan(long id, String type, long views, long outbounds) {
        return new PlanStat(id, "p" + id, "nt", "토리모바일", type, "LGU", "LTE", 10000, views, views, 0, outbounds);
    }

    private final List<PlanStat> plans = List.of(
            plan(1, "PREPAID", 30, 3),
            plan(2, "POSTPAID", 70, 2),
            plan(3, "PREPAID", 20, 1));

    @Test
    void keyMetricsSplitSearchPrepaidAndInternetOutbounds() {
        List<Visit> byRef = List.of(new Visit("SEARCH", 40, 30, 45, 120), new Visit("DIRECT", 10, 5, 12, 30));
        List<PageStat> pages = List.of(new PageStat("OUT", 9, 8, 0, 0), new PageStat("HOME", 50, 40, 0, 0));

        KeyMetrics k = DashboardService.keyMetrics(plans, byRef, pages);

        assertThat(k.searchSessions()).isEqualTo(45);
        assertThat(k.planViews()).isEqualTo(120);
        assertThat(k.prepaidViews()).isEqualTo(50);
        assertThat(k.planOutbounds()).isEqualTo(6);
        assertThat(k.prepaidOutbounds()).isEqualTo(4);
        assertThat(k.internetOutbounds()).isEqualTo(3); // 이동 안내 9 − 요금제 개통하기 6
    }

    @Test
    void keyMetricsAreZeroWithoutData() {
        KeyMetrics k = DashboardService.keyMetrics(List.of(), List.of(), List.of());

        assertThat(k).isEqualTo(new KeyMetrics(0, 0, 0, 0, 0, 0));
    }

    @Test
    void planTypesSumByTypeOrderedByViews() {
        assertThat(DashboardService.planTypes(plans)).containsExactly(
                new TypeStat("POSTPAID", 70, 2),
                new TypeStat("PREPAID", 50, 4));
    }
}
