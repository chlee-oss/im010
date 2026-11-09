package kr.co.im010.api.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
import kr.co.im010.core.row.PlanEndedRow;
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
                "/", "/postpaid", "/prepaid", "/internet", "/faq", "/ai", "/ai/mobile", "/ai/misc",
                "/ai/posts/6/11%EC%9B%94%20%EC%9A%94%EA%B8%88%EC%A0%9C%20%26%20%ED%98%9C%ED%83%9D",
                "/ai/posts/2/old-post", "/plans/29", "/plans/24");
        java.util.Map<String, LocalDate> lastmod = new java.util.HashMap<>();
        urls.forEach(u -> lastmod.put(u.path(), u.lastmod()));
        // 메인 = 요금제 수집일 · 최신 글 중 늦은 날 (한국 날짜)
        assertThat(lastmod.get("/")).isEqualTo(LocalDate.of(2026, 11, 10));
        // 서비스 랜딩 = 그 종류 요금제의 최신 수집일, 인터넷은 없음
        assertThat(lastmod.get("/postpaid")).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(lastmod.get("/prepaid")).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(lastmod.get("/internet")).isNull();
        assertThat(lastmod.get("/faq")).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(lastmod.get("/ai/mobile")).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(lastmod.get("/ai/misc")).isNull();   // 글 없는 카테고리
        // 본문 수정일이 있으면 그 날짜
        assertThat(lastmod.get("/ai/posts/2/old-post")).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(lastmod.get("/plans/29")).isEqualTo(LocalDate.of(2026, 11, 8));
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
    void recentlyEndedPlansStayWithEndDateAsLastmod() {
        givenData();
        // 2026-12-04 23:30 KST (= 14:30 UTC) — 30일 = 11/05 ~ 12/04
        service.clock = Clock.fixed(Instant.parse("2026-12-04T14:30:00Z"), ZoneOffset.UTC);
        when(planMapper.findEndedSince(LocalDate.of(2026, 11, 5))).thenReturn(List.of(
                new PlanEndedRow(4, LocalDate.of(2026, 11, 20)), new PlanEndedRow(1, LocalDate.of(2026, 11, 5))));

        List<SitemapService.Url> urls = service.urls();

        assertThat(urls.subList(urls.size() - 2, urls.size())).containsExactly(
                new SitemapService.Url("/plans/4", LocalDate.of(2026, 11, 20)),
                new SitemapService.Url("/plans/1", LocalDate.of(2026, 11, 5)));
        verify(planMapper).findEndedSince(LocalDate.of(2026, 11, 5));
    }

    @Test
    void endedWindowMovesWithKoreanDate() {
        givenData();
        // 12/05 00:10 KST (= 12/04 15:10 UTC) 이면 11/05 종료 건은 빠진다 → 기준일 11/06
        service.clock = Clock.fixed(Instant.parse("2026-12-04T15:10:00Z"), ZoneOffset.UTC);
        service.urls();
        verify(planMapper).findEndedSince(LocalDate.of(2026, 11, 6));
    }

    @Test
    void encodeMatchesEncodeUriComponent() {
        assertThat(SitemapService.encode("a-b_c.d!~*'()")).isEqualTo("a-b_c.d!~*'()");
        assertThat(SitemapService.encode("가 /?#&")).isEqualTo("%EA%B0%80%20%2F%3F%23%26");
    }
}
