package kr.co.im010.api.seo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import kr.co.im010.core.content.SiteMeta;
import kr.co.im010.core.mapper.SettingsMapper;

/**
 * 검색엔진설정(ST-08) 값을 고객 화면에 반영한다.
 * - 메인(/) · 기본 틀(_shell.html, 미리 렌더링 뒤 새로 생긴 요금제 · 글)은 nginx 가 여기로 넘기고,
 *   배포된 정적 HTML(im010.web.dir)의 제목 · 설명 · og:title · og:description 만 바꿔 응답한다.
 *   api 가 응답하지 못하면 nginx 가 정적 파일을 그대로 낸다 (deploy/nginx/im010.conf).
 * - 설정은 1분마다 다시 읽는다 (백오피스 저장 → 1분 안에 반영). 파일이 바뀌면(배포 · 매일 미리 렌더링) 바로 다시 읽는다.
 */
@Service
public class SiteMetaService {

    private static final Logger log = LoggerFactory.getLogger(SiteMetaService.class);
    static final Duration TTL = Duration.ofMinutes(1);

    /** 응답할 수 있는 화면 → 배포 폴더 안 파일 */
    public enum Page {
        HOME("index.html"), SHELL("_shell.html");

        final String file;

        Page(String file) {
            this.file = file;
        }
    }

    private record CachedMeta(SiteMeta meta, Instant at) {
    }

    private record CachedHtml(String html, SiteMeta meta, long modified, Instant at) {
    }

    private static final Pattern TITLE = Pattern.compile("<title>[\\s\\S]*?</title>");

    private final SettingsMapper settings;
    private final Path webDir;
    private final Clock clock;
    private volatile CachedMeta meta;
    private final Map<Page, CachedHtml> html = new ConcurrentHashMap<>();

    @Autowired
    public SiteMetaService(SettingsMapper settings, @Value("${im010.web.dir:/opt/im010/web}") String webDir) {
        this(settings, Path.of(webDir), Clock.systemUTC());
    }

    SiteMetaService(SettingsMapper settings, Path webDir, Clock clock) {
        this.settings = settings;
        this.webDir = webDir;
        this.clock = clock;
    }

    /** 지금 설정 (1분 캐시). DB 를 읽지 못하면 직전 값, 그것도 없으면 기본값 */
    public SiteMeta current() {
        CachedMeta c = meta;
        Instant now = clock.instant();
        if (c != null && now.isBefore(c.at().plus(TTL))) {
            return c.meta();
        }
        try {
            SiteMeta m = SiteMeta.load(settings);
            meta = new CachedMeta(m, now);
            return m;
        } catch (RuntimeException e) {
            log.warn("검색엔진설정을 읽지 못했습니다 — {}: {}", c == null ? "기본값" : "직전 값", e.toString());
            SiteMeta fallback = c == null ? SiteMeta.defaults() : c.meta();
            meta = new CachedMeta(fallback, now);
            return fallback;
        }
    }

    /** 배포된 HTML 에 설정을 끼운 결과. 파일이 없으면 빈 값 (nginx 가 정적 파일로 대신 응답) */
    public Optional<String> html(Page page) {
        Path file = webDir.resolve(page.file);
        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            SiteMeta m = current();
            CachedHtml c = html.get(page);
            if (c != null && c.modified() == modified && c.meta().equals(m)) {
                return Optional.of(c.html());
            }
            String out = apply(Files.readString(file, StandardCharsets.UTF_8), m);
            html.put(page, new CachedHtml(out, m, modified, clock.instant()));
            return Optional.of(out);
        } catch (IOException e) {
            log.warn("{} 을(를) 읽지 못했습니다: {}", file, e.toString());
            return Optional.empty();
        }
    }

    /** &lt;title&gt; · description · og:title · og:description 만 바꾼다 (없는 태그는 &lt;/head&gt; 앞에 넣는다) */
    static String apply(String html, SiteMeta m) {
        String out = TITLE.matcher(html).find()
                ? TITLE.matcher(html).replaceFirst(Matcher.quoteReplacement("<title>" + esc(m.title()) + "</title>"))
                : insert(html, "<title>" + esc(m.title()) + "</title>");
        out = meta(out, "name", "description", m.description());
        out = meta(out, "property", "og:title", m.effectiveOgTitle());
        out = meta(out, "property", "og:description", m.effectiveOgDescription());
        return out;
    }

    private static String meta(String html, String attr, String name, String content) {
        Pattern p = Pattern.compile("(<meta\\s+" + attr + "=\"" + Pattern.quote(name) + "\"\\s+content=\")[^\"]*(\")");
        Matcher mt = p.matcher(html);
        if (mt.find()) {
            return mt.replaceAll("$1" + Matcher.quoteReplacement(esc(content)) + "$2");
        }
        return insert(html, "<meta " + attr + "=\"" + name + "\" content=\"" + esc(content) + "\" />");
    }

    private static String insert(String html, String tag) {
        int i = html.indexOf("</head>");
        return i < 0 ? html : html.substring(0, i) + "  " + tag + "\n  " + html.substring(i);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
