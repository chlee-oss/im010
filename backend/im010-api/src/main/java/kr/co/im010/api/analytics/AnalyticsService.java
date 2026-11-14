package kr.co.im010.api.analytics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.AnalyticsRows.Event;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고객 화면 이용 통계 수집 (대시보드 DS-01~03).
 * 개인을 알아볼 수 있는 값은 받지 않는다 — 방문자 · 세션 ID는 브라우저가 만든 무작위 값, IP · User-Agent 는 저장하지 않는다.
 * 수집 켜기 · 끄기는 백오피스(admin_setting analytics_enabled)에서 하고, 여기서는 1분마다 다시 읽는다.
 * 형식이 맞지 않는 이벤트는 조용히 버린다 (고객 화면 동작에는 영향 없음).
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final long CACHE_MS = 60_000;

    static final int MAX_EVENTS = 50;
    private static final int MAX_PROPS_JSON = 1000;
    private static final int MAX_VALUE = 100;

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9-]{8,36}$");
    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|headless|lighthouse|preview|scrap|facebookexternalhit|curl|wget|python|java/|okhttp|go-http",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> NAMES = Set.of("page_view", "engage", "click", "calc", "filter", "read_complete");
    private static final Set<String> PAGE_TYPES = Set.of("HOME", "POSTPAID", "PREPAID", "INTERNET", "PLAN", "AI_LIST", "AI_POST",
            "FAQ", "TERMS", "OUT", "NOT_FOUND");
    private static final Set<String> REFS = Set.of("SEARCH", "SOCIAL", "DIRECT", "OTHER");
    private static final Set<String> DEVICES = Set.of("PC", "MO");
    private static final Set<String> PROP_KEYS = Set.of("gb", "network", "found", "dim", "value", "pos", "target", "kind", "id",
            "engine");

    /** 한 번에 보내는 묶음 */
    public record Batch(String vid, String sid, Boolean newVisitor, String ref, String device, List<In> events) {
    }

    /** 이벤트 1건 */
    public record In(String name, String path, String pageType, Long planId, Long postId, Integer durationMs,
                     Map<String, Object> props) {
    }

    private final SettingsMapper settings;
    private final AnalyticsWriter writer;
    private final Clock clock;
    private volatile boolean enabled;
    private volatile boolean checked;
    private volatile long checkedAt;

    @Autowired
    public AnalyticsService(SettingsMapper settings, AnalyticsWriter writer) {
        this(settings, writer, Clock.systemUTC());
    }

    AnalyticsService(SettingsMapper settings, AnalyticsWriter writer, Clock clock) {
        this.settings = settings;
        this.writer = writer;
        this.clock = clock;
    }

    /** 수집 중인지 (1분 캐시) */
    public boolean enabled() {
        long now = clock.millis();
        if (!checked || now - checkedAt > CACHE_MS) {
            try {
                enabled = "true".equals(settings.findSetting("analytics_enabled"));
            } catch (RuntimeException e) {
                log.warn("analytics_enabled read failed", e);
                enabled = false;
            }
            checkedAt = now;
            checked = true;
        }
        return enabled;
    }

    /** 받은 묶음을 걸러 저장한다. 저장한 건수를 돌려준다 (꺼짐 · 봇 · 형식 오류는 0) */
    public int collect(Batch batch, String userAgent) {
        if (!enabled() || batch == null || batch.events() == null || isBot(userAgent)) {
            return 0;
        }
        List<Event> rows = toRows(batch, LocalDate.ofInstant(Instant.now(clock), KST));
        if (!rows.isEmpty()) {
            writer.write(rows);
        }
        return rows.size();
    }

    /** 본문(JSON) → 묶음. 읽을 수 없거나 너무 크면 null */
    public static Batch parse(String body) {
        if (body == null || body.isBlank() || body.length() > 64_000) {
            return null;
        }
        try {
            return JSON.readValue(body, Batch.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find();
    }

    static List<Event> toRows(Batch b, LocalDate day) {
        if (!valid(ID, b.vid()) || !valid(ID, b.sid()) || !REFS.contains(b.ref()) || !DEVICES.contains(b.device())) {
            return List.of();
        }
        List<Event> out = new ArrayList<>();
        for (In e : b.events().subList(0, Math.min(b.events().size(), MAX_EVENTS))) {
            if (e == null || !NAMES.contains(e.name()) || !PAGE_TYPES.contains(e.pageType()) || e.path() == null
                    || !e.path().startsWith("/")) {
                continue;
            }
            Integer duration = e.durationMs() == null ? null : Math.max(0, Math.min(e.durationMs(), 3_600_000));
            out.add(new Event(day, b.vid(), b.sid(), Boolean.TRUE.equals(b.newVisitor()), e.name(), e.pageType(),
                    cut(e.path(), 200), b.ref(), b.device(), positive(e.planId()), positive(e.postId()), duration,
                    propsJson(e.props())));
        }
        return out;
    }

    /** 정해 둔 키의 문자 · 숫자 · 참거짓만 남긴다 */
    static String propsJson(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        props.forEach((k, v) -> {
            if (PROP_KEYS.contains(k)) {
                if (v instanceof String s) {
                    clean.put(k, cut(s, MAX_VALUE));
                } else if (v instanceof Number || v instanceof Boolean) {
                    clean.put(k, String.valueOf(v).toLowerCase(Locale.ROOT));
                }
            }
        });
        if (clean.isEmpty()) {
            return null;
        }
        String json = JSON.writeValueAsString(clean);
        return json.length() > MAX_PROPS_JSON ? null : json;
    }

    private static boolean valid(Pattern p, String s) {
        return s != null && p.matcher(s).matches();
    }

    private static Long positive(Long id) {
        return id != null && id > 0 ? id : null;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
