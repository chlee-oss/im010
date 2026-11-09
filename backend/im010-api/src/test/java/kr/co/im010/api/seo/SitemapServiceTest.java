package kr.co.im010.api.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.co.im010.api.config.Im010Properties;
import kr.co.im010.core.mapper.AiMapper;
import kr.co.im010.core.mapper.ContentMapper;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.AiCategoryRow;
import kr.co.im010.core.row.AiSitemapRow;
import kr.co.im010.core.row.FaqRow;
import kr.co.im010.core.row.PlanRow;

class SitemapServiceTest {

    PlanMapper planMapper = mock(PlanMapper.class);
    AiMapper aiMapper = mock(AiMapper.class);
    ContentMapper contentMapper = mock(ContentMapper.class);
    SitemapService service = new SitemapService(planMapper, aiMapper, contentMapper,
            new Im010Properties("https://www.im010.co.kr/"));

    static PlanRow plan(long id, String type, LocalDate collectedOn) {
        return new PlanRow(id, "mv", "마블링", type, "PUBLISHED", "넉넉 11GB+", "데이터 11GB", BigDecimal.TEN, null, null,
                null, "SKT", "LTE", 10000, null, null, null, null, "", collectedOn, null, null);
    }

    static OffsetDateTime utc(int month, int day, int hour) {
        return OffsetDateTime.of(2026, month, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    void givenData() {
        when(planMapper.findPublished("POSTPAID", null)).thenReturn(List.of(plan(29, "POSTPAID", LocalDate.of(2026, 11, 8))));
        when(planMapper.findPublished("PREPAID", null)).thenReturn(List.of(plan(24, "PREPAID", LocalDate.of(2026, 11, 5))));
        when(aiMapper.findCategories(true)).thenReturn(List.of(
                new AiCategoryRow("MOBILE", "알뜰통신", "mobile", "#fff", "#000", 1, true, 1),
                new AiCategoryRow("MISC", "잡식잡식", "misc", "#fff", "#000", 3, true, 0)));
        when(aiMapper.findSitemapEntries()).thenReturn(List.of(
                // 11/9 16:00 UTC = 11/10 01:00 KST
                new AiSitemapRow(6, "11월 요금제 & 혜택", "mobile", utc(11, 9, 16), null),
                new AiSitemapRow(2, "old-post", "mobile", utc(10, 1, 0), utc(11, 3, 0))));
        when(contentMapper.findFaqs(true)).thenReturn(List.of(
                new FaqRow(1, "개통", "Q", "A", 1, true, "admin", utc(10, 20, 3))));
    }

    @Test
    void listsPublicPagesWithLastmod() {
        givenData();
        List<SitemapService.Url> urls = service.urls();

        assertThat(urls).extracting(SitemapService.Url::path).containsExactly(
                "/", "/faq", "/ai", "/ai/mobile", "/ai/misc",
                "/ai/posts/6/11%EC%9B%94%20%EC%9A%94%EA%B8%88%EC%A0%9C%20%26%20%ED%98%9C%ED%83%9D",
                "/ai/posts/2/old-post", "/plans/29", "/plans/24");
        // 메인 = 요금제 수집일 · 최신 글 중 늦은 날 (한국 날짜)
        assertThat(urls.get(0).lastmod()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(urls.get(1).lastmod()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(urls.get(3).lastmod()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(urls.get(4).lastmod()).isNull();   // 글 없는 카테고리
        // 본문 수정일이 있으면 그 날짜
        assertThat(urls.get(6).lastmod()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(urls.get(7).lastmod()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void xmlIsEscapedAndUsesSiteUrl() {
        givenData();
        String xml = service.xml();
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<url><loc>https://www.im010.co.kr/</loc><lastmod>2026-11-10</lastmod></url>");
        assertThat(xml).contains("<url><loc>https://www.im010.co.kr/ai/misc</loc></url>");
        assertThat(xml).contains("<loc>https://www.im010.co.kr/plans/29</loc><lastmod>2026-11-08</lastmod>");
        assertThat(xml).doesNotContain("&ED").doesNotContain(" & ");
    }

    @Test
    void encodeMatchesEncodeUriComponent() {
        assertThat(SitemapService.encode("a-b_c.d!~*'()")).isEqualTo("a-b_c.d!~*'()");
        assertThat(SitemapService.encode("가 /?#&")).isEqualTo("%EA%B0%80%20%2F%3F%23%26");
    }
}
