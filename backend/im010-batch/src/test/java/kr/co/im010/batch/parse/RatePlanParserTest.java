package kr.co.im010.batch.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class RatePlanParserTest {

    private final RatePlanParser parser = new RatePlanParser();

    private Document load(String name, String baseUri) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return Jsoup.parse(in, "UTF-8", baseUri);
        }
    }

    @Test
    void 카드형_페이지에서_요금제와_탭을_추출한다() throws IOException {
        String source = "https://partner.example.com/rate_plan.do?type=T007";
        ParsedPage page = parser.parse(load("card.html", source), source, "이벤트 요금제");
        List<ParsedPlan> plans = page.plans();

        assertThat(plans).hasSize(4);

        ParsedPlan p1 = plans.get(0);
        assertThat(p1.partnerPlanCode()).isEqualTo("2506");
        assertThat(p1.name()).isEqualTo("예시 프라임 7GB + 1Mbps");
        assertThat(p1.network()).isEqualTo("LGU");
        assertThat(p1.generation()).isEqualTo("LTE");
        assertThat(p1.dataText()).isEqualTo("데이터 7GB");
        assertThat(p1.dataGb()).isEqualByComparingTo("7");
        assertThat(p1.qosText()).isEqualTo("1Mbps");          // 데이터 줄에 없으면 요금제명에서
        assertThat(p1.voiceText()).isEqualTo("무제한");
        assertThat(p1.smsText()).isEqualTo("무제한");
        assertThat(p1.price()).isEqualTo(7020);
        assertThat(p1.discountMonths()).isEqualTo(7);
        assertThat(p1.priceAfterDiscount()).isEqualTo(18900);
        assertThat(p1.detailUrl()).isEqualTo("https://partner.example.com/rateplan_view.do?type=T007&no=2506");
        assertThat(p1.siteOrder()).isEqualTo(1);

        ParsedPlan p2 = plans.get(1);
        assertThat(p2.generation()).isEqualTo("5G");
        assertThat(p2.dataText()).isEqualTo("데이터 11GB + 매일 2GB");
        assertThat(p2.qosText()).isEqualTo("3Mbps");          // 안내 문구 "소진 후 최대 3Mbps"
        assertThat(p2.voiceText()).isEqualTo("기본 제공");
        assertThat(p2.price()).isEqualTo(23100);              // 할인 전 가격(p.origin)은 빼고
        assertThat(p2.discountMonths()).isEqualTo(7);         // "8개월 차부터" = 7개월 할인
        assertThat(p2.priceAfterDiscount()).isEqualTo(29700);

        ParsedPlan p3 = plans.get(2);
        assertThat(p3.name()).isEqualTo("[평생할인] 예시 200분 10GB");   // 장식 배지만 제거
        assertThat(p3.network()).isEqualTo("SKT");            // 망 배지
        assertThat(p3.voiceText()).isEqualTo("200분");
        assertThat(p3.smsText()).isEqualTo("100건");
        assertThat(p3.price()).isEqualTo(10);
        assertThat(p3.discountMonths()).isNull();             // 평생할인은 기간 없음

        ParsedPlan p4 = plans.get(3);
        assertThat(p4.network()).isEqualTo("KT");
        assertThat(p4.dataGb()).isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(p4.price()).isNull();
        assertThat(p4.missingRequired()).isTrue();

        assertThat(page.tabs()).extracting(ParsedPage.Tab::url).containsExactly(
                "https://partner.example.com/rate_plan.do?type=T007",
                "https://partner.example.com/rate_plan.do?type=T003",
                "https://partner.example.com/rate_plan.do?type=T009");
        assertThat(page.tabs()).extracting(ParsedPage.Tab::label).containsExactly("이벤트 요금제", "LTE 요금제", "복지 요금제");
    }

    @Test
    void 목록형_페이지에서_요금제를_추출한다() throws IOException {
        String source = "https://partner.example.com/rate_plan.do?type=T008";
        ParsedPage page = parser.parse(load("board.html", source), source, null);

        assertThat(page.plans()).hasSize(2);
        ParsedPlan p1 = page.plans().get(0);
        assertThat(p1.partnerPlanCode()).isEqualTo("327");
        assertThat(p1.name()).isEqualTo("예시 WELL 71GB+");
        assertThat(p1.network()).isEqualTo("LGU");
        assertThat(p1.dataText()).isEqualTo("데이터 11GB + 매일 2GB");
        assertThat(p1.dataGb()).isEqualByComparingTo("11");
        assertThat(p1.qosText()).isEqualTo("3Mbps");
        assertThat(p1.price()).isEqualTo(22000);
        assertThat(p1.discountMonths()).isEqualTo(24);
        assertThat(p1.priceAfterDiscount()).isEqualTo(49500);

        ParsedPlan p2 = page.plans().get(1);
        assertThat(p2.network()).isEqualTo("KT");
        assertThat(p2.dataGb()).isEqualByComparingTo("5.4");
        assertThat(p2.qosText()).isEqualTo("1Mbps");
        assertThat(p2.price()).isEqualTo(16500);
        assertThat(p2.discountMonths()).isNull();

        assertThat(page.tabs()).extracting(ParsedPage.Tab::label).containsExactly("✨ 인기요금제", "💳선불요금제");
    }

    @Test
    void 문구_해석() {
        assertThat(SpecText.data("월 데이터 7 GB + 1Mbps")).isEqualTo(new SpecText.Data("데이터 7GB", new BigDecimal("7"), "1Mbps"));
        assertThat(SpecText.data("300MB + 3Mbps").gb()).isEqualByComparingTo("0.3");
        assertThat(SpecText.data("0.7GB").text()).isEqualTo("데이터 0.7GB");
        assertThat(SpecText.data("데이터 무제한")).isEqualTo(new SpecText.Data("데이터 무제한", null, null));
        assertThat(SpecText.data("일 2GB + 3Mbps")).isEqualTo(new SpecText.Data("데이터 매일 2GB", null, "3Mbps"));
        assertThat(SpecText.data("400Kbps").qos()).isEqualTo("400Kbps");
        // 월 · 일 두 줄로 나온 데이터 (파서가 " + " 로 이어 붙임)
        assertThat(SpecText.data("월 데이터 기본제공 + 5Mbps + 일 데이터 5GB"))
                .isEqualTo(new SpecText.Data("데이터 매일 5GB", null, "5Mbps"));

        assertThat(SpecText.voice("음성통화 없음")).isEqualTo("없음");
        assertThat(SpecText.voice("통화 1,000 분")).isEqualTo("1,000분");
        assertThat(SpecText.sms("문자 기본 제공")).isEqualTo("기본 제공");

        assertThat(SpecText.won("월 36,000 원")).isEqualTo(36000);
        assertThat(SpecText.discount("*6개월 이후 19,500원", 19500)).isNull();   // 할인 후 요금이 같으면 할인 아님
        assertThat(SpecText.discount("*12개월 이후 11,010원", 10)).isEqualTo(new SpecText.Discount(12, 11010));
    }

    @Test
    void 세대_판별() {
        assertThat(RatePlanParser.generation("시월 통화기본매일5GB+", null)).isEqualTo("LTE");
        assertThat(RatePlanParser.generation("데이터 15G", null)).isEqualTo("LTE");
        assertThat(RatePlanParser.generation("시월5G 120분6GB", null)).isEqualTo("5G");
        assertThat(RatePlanParser.generation("LGU+ 5G 슈가 95GB+", null)).isEqualTo("5G");
        assertThat(RatePlanParser.generation("프라임 12GB", "5G 무제한 요금제")).isEqualTo("5G");
    }

    @Test
    void URL_정리() {
        assertThat(Urls.clean("https://a.com/rate_plan.do;jsessionid=7CAA?type=T007&subtype=&no=1"))
                .isEqualTo("https://a.com/rate_plan.do?type=T007&no=1");
        assertThat(Urls.clean("https://a.com/rate_plan.do;jsessionid=7CAA")).isEqualTo("https://a.com/rate_plan.do");
        assertThat(Urls.param("https://a.com/x.do?type=T1&no=25", "no")).isEqualTo("25");
    }
}
