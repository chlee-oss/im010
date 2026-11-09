package kr.co.im010.api.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import kr.co.im010.api.seo.SiteMetaService.Page;
import kr.co.im010.core.content.SiteMeta;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.SettingRow;

class SiteMetaServiceTest {

    static final String HTML = """
            <!doctype html>
            <html lang="ko">
              <head>
                <title>im010 — 알뜰폰 요금비교</title>
                <meta name="description" content="옛 설명" />
                <meta property="og:title" content="im010 — 알뜰폰 요금비교" />
                <meta property="og:description" content="옛 설명" />
                <meta property="og:image" content="https://www.im010.co.kr/og-default.png" />
              </head>
              <body><div id="root"><h1>본문</h1></div></body>
            </html>
            """;

    @TempDir
    Path dir;

    static SettingRow row(String key, String value) {
        return new SettingRow(key, value, "admin", null);
    }

    static SettingsMapper mapper(String title, String desc, String ogTitle, String ogDesc) {
        SettingsMapper m = mock(SettingsMapper.class);
        when(m.findSettings(any())).thenReturn(List.of(row(SiteMeta.TITLE, title), row(SiteMeta.DESCRIPTION, desc),
                row(SiteMeta.OG_TITLE, ogTitle), row(SiteMeta.OG_DESCRIPTION, ogDesc)));
        return m;
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-09T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void applyReplacesOnlyTitleDescriptionAndOg() {
        String out = SiteMetaService.apply(HTML, new SiteMeta("새 제목 & \"따옴표\"", "새 설명", "공유 제목", ""));

        assertThat(out).contains("<title>새 제목 &amp; &quot;따옴표&quot;</title>")
                .contains("<meta name=\"description\" content=\"새 설명\" />")
                .contains("<meta property=\"og:title\" content=\"공유 제목\" />")
                .contains("<meta property=\"og:description\" content=\"새 설명\" />")   // 비어 있으면 사이트 설명
                .contains("og-default.png")
                .contains("<div id=\"root\"><h1>본문</h1></div>")
                .doesNotContain("옛 설명");
    }

    @Test
    void applyInsertsMissingTags() {
        String out = SiteMetaService.apply("<html><head></head><body></body></html>", new SiteMeta("T", "D", "", ""));

        assertThat(out).contains("<title>T</title>").contains("<meta name=\"description\" content=\"D\" />")
                .contains("<meta property=\"og:title\" content=\"T\" />").contains("</head>");
    }

    @Test
    void blankSettingsFallBackToDefaults() {
        SiteMeta m = new SiteMeta("  ", null, " ", null);

        assertThat(m.title()).isEqualTo(SiteMeta.DEFAULT_TITLE);
        assertThat(m.description()).isEqualTo(SiteMeta.DEFAULT_DESCRIPTION);
        assertThat(m.effectiveOgTitle()).isEqualTo(SiteMeta.DEFAULT_TITLE);
    }

    @Test
    void htmlUsesSettingsAndCachesForOneMinute() throws Exception {
        Files.writeString(dir.resolve("index.html"), HTML, StandardCharsets.UTF_8);
        SettingsMapper m = mapper("설정 제목", "설정 설명", "", "");
        MutableClock clock = new MutableClock();
        SiteMetaService s = new SiteMetaService(m, dir, clock);

        assertThat(s.html(Page.HOME)).get().asString().contains("<title>설정 제목</title>");
        s.html(Page.HOME);
        verify(m, times(1)).findSettings(any());

        clock.now = clock.now.plus(SiteMetaService.TTL).plusSeconds(1);
        s.html(Page.HOME);
        verify(m, times(2)).findSettings(any());
    }

    @Test
    void missingFileIsEmpty() {
        SiteMetaService s = new SiteMetaService(mapper("a", "b", "", ""), dir, new MutableClock());

        assertThat(s.html(Page.SHELL)).isEmpty();
    }

    @Test
    void dbErrorKeepsDefaults() {
        SettingsMapper m = mock(SettingsMapper.class);
        when(m.findSettings(any())).thenThrow(new IllegalStateException("db down"));

        assertThat(new SiteMetaService(m, dir, new MutableClock()).current()).isEqualTo(SiteMeta.defaults());
    }
}
