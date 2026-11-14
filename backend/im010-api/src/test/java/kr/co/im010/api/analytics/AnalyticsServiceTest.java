package kr.co.im010.api.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kr.co.im010.api.analytics.AnalyticsService.Batch;
import kr.co.im010.api.analytics.AnalyticsService.In;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.AnalyticsRows.Event;

class AnalyticsServiceTest {

    private static final String VID = "0f8e7d6c-5b4a-4321-9876-0123456789ab";
    private static final String SID = "aaaa1111-bbbb-2222-cccc-333344445555";
    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0 Safari/537.36";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    private static Batch batch(In... events) {
        return new Batch(VID, SID, true, "SEARCH", "MO", List.of(events));
    }

    @Test
    void keepsValidEventsAndDropsUnknownNamesAndPageTypes() {
        List<Event> rows = AnalyticsService.toRows(batch(
                new In("page_view", "/plans/7", "PLAN", 7L, null, null, null),
                new In("hack", "/", "HOME", null, null, null, null),
                new In("page_view", "/x", "ADMIN", null, null, null, null),
                new In("page_view", "http://evil", "HOME", null, null, null, null)), DAY);

        assertThat(rows).hasSize(1);
        Event e = rows.get(0);
        assertThat(e.day()).isEqualTo(DAY);
        assertThat(e.planId()).isEqualTo(7L);
        assertThat(e.newVisitor()).isTrue();
        assertThat(e.device()).isEqualTo("MO");
    }

    @Test
    void rejectsWholeBatchWithBadIdsRefOrDevice() {
        assertThat(AnalyticsService.toRows(new Batch("x", SID, false, "DIRECT", "PC",
                List.of(new In("page_view", "/", "HOME", null, null, null, null))), DAY)).isEmpty();
        assertThat(AnalyticsService.toRows(new Batch(VID, SID, false, "FB", "PC",
                List.of(new In("page_view", "/", "HOME", null, null, null, null))), DAY)).isEmpty();
        assertThat(AnalyticsService.toRows(new Batch(VID, SID, false, "DIRECT", "TV",
                List.of(new In("page_view", "/", "HOME", null, null, null, null))), DAY)).isEmpty();
    }

    @Test
    void capsEventCountDurationAndIds() {
        In many = new In("engage", "/", "HOME", -3L, 0L, 99_999_999, null);
        List<Event> rows = AnalyticsService.toRows(batch(Collections.nCopies(80, many).toArray(In[]::new)), DAY);

        assertThat(rows).hasSize(AnalyticsService.MAX_EVENTS);
        assertThat(rows.get(0).durationMs()).isEqualTo(3_600_000);
        assertThat(rows.get(0).planId()).isNull();
        assertThat(rows.get(0).postId()).isNull();
    }

    @Test
    void propsKeepOnlyKnownKeysAndScalarValues() {
        String json = AnalyticsService.propsJson(Map.of("gb", 11, "found", false, "email", "a@b.c",
                "network", "SKT", "nested", Map.of("x", 1)));

        assertThat(json).contains("\"gb\":\"11\"", "\"found\":\"false\"", "\"network\":\"SKT\"")
                .doesNotContain("email", "nested");
        assertThat(AnalyticsService.propsJson(Map.of("email", "a@b.c"))).isNull();
    }

    @Test
    void acceptsServiceLandingPagesAndSearchEngine() {
        List<Event> rows = AnalyticsService.toRows(batch(
                new In("page_view", "/prepaid", "PREPAID", null, null, null, Map.of("engine", "naver")),
                new In("page_view", "/postpaid", "POSTPAID", null, null, null, null),
                new In("page_view", "/internet", "INTERNET", null, null, null, null)), DAY);

        assertThat(rows).extracting(Event::pageType).containsExactly("PREPAID", "POSTPAID", "INTERNET");
        assertThat(rows.get(0).propsJson()).isEqualTo("{\"engine\":\"naver\"}");
    }

    @Test
    void detectsBotsAndMissingUserAgent() {
        assertThat(AnalyticsService.isBot("Mozilla/5.0 (compatible; Googlebot/2.1)")).isTrue();
        assertThat(AnalyticsService.isBot("Yeti/1.1 (NHN Corp.; crawler)")).isTrue();
        assertThat(AnalyticsService.isBot(null)).isTrue();
        assertThat(AnalyticsService.isBot(CHROME)).isFalse();
    }

    @Test
    void parseIgnoresUnknownFieldsAndBadJson() {
        Batch b = AnalyticsService.parse("{\"vid\":\"" + VID + "\",\"sid\":\"" + SID
                + "\",\"ref\":\"DIRECT\",\"device\":\"PC\",\"extra\":1,\"events\":[{\"name\":\"page_view\",\"path\":\"/\",\"pageType\":\"HOME\"}]}");

        assertThat(b).isNotNull();
        assertThat(b.events()).hasSize(1);
        assertThat(AnalyticsService.parse("{not json")).isNull();
        assertThat(AnalyticsService.parse("")).isNull();
    }

    @Test
    void storesNothingWhileCollectionIsOff() {
        SettingsMapper settings = mock(SettingsMapper.class);
        AnalyticsWriter writer = mock(AnalyticsWriter.class);
        when(settings.findSetting("analytics_enabled")).thenReturn("false");
        AnalyticsService service = new AnalyticsService(settings, writer,
                Clock.fixed(Instant.parse("2026-11-09T01:00:00Z"), ZoneOffset.UTC));

        int saved = service.collect(batch(new In("page_view", "/", "HOME", null, null, null, null)), CHROME);

        assertThat(saved).isZero();
        verify(writer, never()).write(anyList());
    }

    @Test
    void storesWhenOnUsingKoreanDate() {
        SettingsMapper settings = mock(SettingsMapper.class);
        AnalyticsWriter writer = mock(AnalyticsWriter.class);
        when(settings.findSetting("analytics_enabled")).thenReturn("true");
        // UTC 11/08 16:00 = 한국 11/09 01:00
        AnalyticsService service = new AnalyticsService(settings, writer,
                Clock.fixed(Instant.parse("2026-11-08T16:00:00Z"), ZoneOffset.UTC));

        int saved = service.collect(batch(new In("page_view", "/", "HOME", null, null, null, null)), CHROME);

        assertThat(saved).isEqualTo(1);
        verify(writer).write(List.of(new Event(DAY, VID, SID, true, "page_view", "HOME", "/", "SEARCH", "MO", null, null,
                null, null)));
        assertThat(service.collect(batch(new In("page_view", "/", "HOME", null, null, null, null)), "Googlebot")).isZero();
    }
}
