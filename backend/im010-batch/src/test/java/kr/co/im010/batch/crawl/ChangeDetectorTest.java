package kr.co.im010.batch.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.co.im010.batch.crawl.ChangeDetector.Change;
import kr.co.im010.batch.crawl.ChangeDetector.Item;
import kr.co.im010.batch.crawl.ChangeDetector.Result;
import kr.co.im010.batch.crawl.ChangeDetector.Status;
import kr.co.im010.core.parse.ParsedPlan;
import kr.co.im010.core.row.HandledHashRow;
import kr.co.im010.core.row.OpenItemRow;
import kr.co.im010.core.row.PlanBaselineRow;

class ChangeDetectorTest {

    private static ParsedPlan plan(String code, String name, Integer price) {
        return new ParsedPlan(code, name, "LGU", "LTE", "데이터 7GB", new BigDecimal("7"), "1Mbps", "무제한", "무제한",
                price, null, null, "https://p.example.com/rateplan_view.do?no=" + code, "https://p.example.com/rate_plan.do", 1);
    }

    private static PlanBaselineRow baseline(long id, String code, String status, String name, Integer price, String supplemented) {
        return new PlanBaselineRow(id, code, status, name, "데이터 7GB", new BigDecimal("7.00"), "1Mbps", "무제한", "무제한",
                "LGU", "LTE", price, null, null, supplemented, null);
    }

    private static Result detect(List<ParsedPlan> collected, List<PlanBaselineRow> baselines) {
        return ChangeDetector.detect(collected, baselines, List.of(), List.of(), 0.3);
    }

    private static Item item(Result r, String key) {
        return r.items().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void 신규_변경없음_변경_사라짐() {
        Result r = detect(
                List.of(plan("1", "A", 10000), plan("2", "B", 20000), plan("3", "C", 9900)),
                List.of(baseline(11, "2", "PUBLISHED", "B", 20000, null),
                        baseline(12, "3", "PUBLISHED", "C", 15000, null),
                        baseline(13, "4", "PUBLISHED", "D", 5000, null),
                        baseline(14, "5", "ENDED", "E", 5000, null)));

        assertThat(item(r, "1").change()).isEqualTo(Change.NEW);
        assertThat(item(r, "1").status()).isEqualTo(Status.REVIEW_PENDING);

        assertThat(item(r, "2").change()).isEqualTo(Change.UNCHANGED);   // 7 과 7.00 은 같은 값
        assertThat(item(r, "2").status()).isEqualTo(Status.RECORDED);

        Item changed = item(r, "3");
        assertThat(changed.change()).isEqualTo(Change.CHANGED);
        assertThat(changed.changedFields()).containsExactly(ChangeDetector.F_PRICE);
        assertThat(changed.warnings()).contains("PRICE_JUMP");          // 15,000 → 9,900 (34% 하락)

        Item ended = item(r, "4");
        assertThat(ended.change()).isEqualTo(Change.ENDED);
        assertThat(ended.status()).isEqualTo(Status.AUTO_APPLIED);
        assertThat(r.endedPlanIds()).containsExactly(13L);              // 이미 판매 종료된 14 는 그대로
        assertThat(r.touchedPlanIds()).containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void 일_데이터만_있는_요금제는_기본_제공량_경고() {
        ParsedPlan daily = new ParsedPlan("7", "매일 5GB", "LGU", "LTE", "데이터 0GB + 매일 5GB", BigDecimal.ZERO, "5Mbps",
                "기본 제공", "기본 제공", 26400, null, null, null, null, 1);
        ParsedPlan unlimited = new ParsedPlan("8", "무제한", "LGU", "5G", "데이터 무제한", null, null,
                "기본 제공", "기본 제공", 49000, null, null, null, null, 2);

        assertThat(ChangeDetector.warnings(daily)).contains("MISSING_DATA");
        assertThat(ChangeDetector.warnings(unlimited)).doesNotContain("MISSING_DATA");
    }

    @Test
    void 판매_종료된_요금제가_다시_보이면_판매_재개_점검() {
        Result r = detect(List.of(plan("5", "E", 5000)), List.of(baseline(14, "5", "ENDED", "E", 5000, null)));

        Item item = item(r, "5");
        assertThat(item.change()).isEqualTo(Change.CHANGED);
        assertThat(item.status()).isEqualTo(Status.REVIEW_PENDING);
        assertThat(item.warnings()).contains("RESUMED");
    }

    @Test
    void 운영자가_보완한_항목은_수집되지_않아도_변경이_아니다() {
        ParsedPlan noQos = new ParsedPlan("2", "B", "LGU", "LTE", "데이터 7GB", new BigDecimal("7"), null, "무제한", "무제한",
                20000, null, null, null, null, 1);
        Result r = detect(List.of(noQos), List.of(baseline(11, "2", "PUBLISHED", "B", 20000, "qos")));

        assertThat(item(r, "2").change()).isEqualTo(Change.UNCHANGED);
    }

    @Test
    void 점검_대기_건은_하나만_열어_둔다() {
        ParsedPlan p = plan("1", "A", 10000);
        String hash = ChangeDetector.hash(p);

        Result same = ChangeDetector.detect(List.of(p), List.of(), List.of(new OpenItemRow(100, "1", hash)), List.of(), 0.3);
        assertThat(item(same, "1").status()).isEqualTo(Status.RECORDED);
        assertThat(same.supersededItemIds()).isEmpty();

        Result different = ChangeDetector.detect(List.of(p), List.of(), List.of(new OpenItemRow(100, "1", "old")), List.of(), 0.3);
        assertThat(item(different, "1").status()).isEqualTo(Status.REVIEW_PENDING);
        assertThat(different.supersededItemIds()).containsExactly(100L);

        Result gone = ChangeDetector.detect(List.of(), List.of(), List.of(new OpenItemRow(101, "9", "x")), List.of(), 0.3);
        assertThat(gone.supersededItemIds()).containsExactly(101L);    // 점검 대기 중에 사이트에서 사라짐
    }

    @Test
    void 승인된_값과_같으면_변경_없음_제외된_값과_같으면_표시만() {
        ParsedPlan p = plan("3", "C", 9900);
        String hash = ChangeDetector.hash(p);
        List<PlanBaselineRow> baselines = List.of(baseline(12, "3", "PUBLISHED", "C 수정", 9900, null));

        Result approved = ChangeDetector.detect(List.of(p), baselines, List.of(),
                List.of(new HandledHashRow("3", hash, "APPROVED")), 0.3);
        assertThat(item(approved, "3").change()).isEqualTo(Change.UNCHANGED);

        Result excluded = ChangeDetector.detect(List.of(p), baselines, List.of(),
                List.of(new HandledHashRow("3", hash, "EXCLUDED")), 0.3);
        assertThat(item(excluded, "3").status()).isEqualTo(Status.RECORDED);
        assertThat(item(excluded, "3").warnings()).contains("SAME_AS_EXCLUDED");
    }

    @Test
    void 승인_때와_같은_수집값이면_운영자가_고친_값과_달라도_변경_없음() {
        ParsedPlan p = plan("3", "C", 9900);
        PlanBaselineRow edited = new PlanBaselineRow(12, "3", "PUBLISHED", "C (운영자 수정)", "데이터 7GB", new BigDecimal("7"),
                "1Mbps", "무제한", "무제한", "LGU", "LTE", 9900, null, null, "name", ChangeDetector.hash(p));

        Result r = detect(List.of(p), List.of(edited));

        assertThat(item(r, "3").change()).isEqualTo(Change.UNCHANGED);
        assertThat(item(r, "3").status()).isEqualTo(Status.RECORDED);
    }

    @Test
    void 코드가_없으면_요금제명과_망으로_같은_요금제를_찾는다() {
        Result r = detect(List.of(plan(null, "A", 10000)), List.of(baseline(11, null, "PUBLISHED", "A", 10000, null)));

        assertThat(item(r, "A|LGU").change()).isEqualTo(Change.UNCHANGED);
        assertThat(r.endedPlanIds()).isEmpty();
    }
}
