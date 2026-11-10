package kr.co.im010.core.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.CountRow;

/**
 * 정기 현황 리포트 (매일 08:00, 알림 종류 DAILY_REPORT). 배치 스케줄과 백오피스 [지금 보내기]가 같이 쓴다.
 * <pre>
 * 서비스 상태   api · admin 헬스 체크 (배치는 이 리포트를 만드는 쪽이라 정상)
 * 요금제       게시 중(전날 리포트 대비 증감) · 게시 전 · 비노출 · 변경 대기 · 변경 예약 — 요금제관리와 같은 고객 노출 기준
 * 판매 종료     어제 종료된 요금제
 * 고객 이동     어제 개통하기(요금제) · 상담 신청(인터넷) 클릭, 그중 URL 없음 · 판매 종료로 못 보낸 건
 * 처리 대기     점검 대기 · 승인 대기
 * </pre>
 * 전날 대비 증감은 직전 리포트 숫자(admin_setting daily_report_counts)와 비교한다.
 */
public class DailyReport {

    public static final String COUNTS_KEY = "daily_report_counts";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN);
    private static final Map<String, String> TYPE = Map.of("POSTPAID", "후불", "PREPAID", "선불");

    /** 보낼 알림 한 건 + 다음 비교용 숫자 */
    public record Report(String level, String title, String body, String countsJson) {
    }

    /** 서비스 이름 → 헬스 체크 결과 (UP 이면 정상) */
    public record Health(String name, String status) {
    }

    private final NotifyMapper notifyMapper;
    private final SettingsMapper settings;
    private final Map<String, String> healthUrls;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /** @param healthUrls 이름 → 헬스 체크 주소 (예: api → http://127.0.0.1:8080/actuator/health) */
    public DailyReport(NotifyMapper notifyMapper, SettingsMapper settings, Map<String, String> healthUrls) {
        this.notifyMapper = notifyMapper;
        this.settings = settings;
        this.healthUrls = healthUrls;
    }

    /** 오늘 리포트를 만든다 (어제 하루 기준). 직전 숫자를 오늘 숫자로 바꾸는 건 save() */
    public Report build(LocalDate today, ZoneId zone) {
        LocalDate yesterday = today.minusDays(1);
        List<Health> health = new ArrayList<>();
        healthUrls.forEach((name, url) -> health.add(new Health(name, check(url))));
        return render(today, health, notifyMapper.countPlanStatus(), notifyMapper.countEndedOn(yesterday),
                notifyMapper.countForwards(yesterday.atStartOfDay(zone).toOffsetDateTime(), today.atStartOfDay(zone).toOffsetDateTime()),
                notifyMapper.countOpenItems(), settings.findSetting(COUNTS_KEY));
    }

    /** 다음 리포트의 증감 기준으로 오늘 숫자를 남긴다 */
    public void save(Report r) {
        settings.updateSetting(COUNTS_KEY, r.countsJson(), "batch");
    }

    static Report render(LocalDate today, List<Health> health, List<CountRow> status, List<CountRow> ended,
                         List<CountRow> forwards, List<CountRow> open, String previousJson) {
        Map<String, Long> s = toMap(status);
        Map<String, Long> prev = previous(previousJson);
        List<String> lines = new ArrayList<>();

        // 서비스 상태
        List<String> down = health.stream().filter(h -> !"UP".equals(h.status())).map(h -> h.name() + " " + h.status()).toList();
        lines.add("**서비스 상태** " + (down.isEmpty()
                ? "✅ 정상 (" + String.join(" · ", health.stream().map(Health::name).toList()) + (health.isEmpty() ? "" : " · ") + "batch)"
                : "🚨 확인 필요 — " + String.join(", ", down)));

        // 요금제
        lines.add("");
        lines.add("**요금제** (판매 종료 제외)");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String type : List.of("POSTPAID", "PREPAID")) {
            long pub = s.getOrDefault("PUBLISHED:" + type, 0L);
            counts.put(type, pub);
            Long before = prev.get(type);
            String diff = before == null ? "" : " (" + signed(pub - before) + ")";
            lines.add("- " + TYPE.get(type) + " 게시 중 " + pub + "건" + diff
                    + extra(s, type));
        }
        long draft = s.getOrDefault("CHANGE_DRAFT:POSTPAID", 0L) + s.getOrDefault("CHANGE_DRAFT:PREPAID", 0L);
        long scheduled = s.getOrDefault("CHANGE_SCHEDULED:POSTPAID", 0L) + s.getOrDefault("CHANGE_SCHEDULED:PREPAID", 0L);
        lines.add("- 게시하지 않은 변경: 대기 " + draft + "건 · 예약 " + scheduled + "건");

        // 어제 판매 종료
        Map<String, Long> e = toMap(ended);
        long endedTotal = e.values().stream().mapToLong(Long::longValue).sum();
        lines.add("- 어제 판매 종료 " + endedTotal + "건" + (endedTotal == 0 ? ""
                : " (" + joinTypes(e) + ")"));

        // 고객 이동
        lines.add("");
        lines.add("**어제 고객 이동**");
        lines.add("- 개통하기(요금제) " + forwardLine(forwards, "PLAN"));
        lines.add("- 상담 신청(인터넷) " + forwardLine(forwards, "INTERNET"));

        // 처리 대기
        Map<String, Long> o = toMap(open);
        long review = o.getOrDefault("REVIEW_PENDING", 0L);
        long approval = o.getOrDefault("APPROVAL_REQUESTED", 0L);
        lines.add("");
        lines.add("**처리 대기** 점검 대기 " + review + "건 · 승인 대기 " + approval + "건");

        String level = !down.isEmpty() ? "URGENT" : "INFO";
        String title = today.format(DAY) + " im010 현황";
        Map<String, Object> save = new LinkedHashMap<>();
        save.put("date", today.toString());
        save.putAll(counts);
        return new Report(level, title, String.join("\n", lines), JSON.writeValueAsString(save));
    }

    private static String extra(Map<String, Long> s, String type) {
        List<String> parts = new ArrayList<>();
        long unpublished = s.getOrDefault("UNPUBLISHED:" + type, 0L);
        long hidden = s.getOrDefault("HIDDEN:" + type, 0L);
        if (unpublished > 0) {
            parts.add("게시 전 " + unpublished);
        }
        if (hidden > 0) {
            parts.add("비노출 " + hidden);
        }
        return parts.isEmpty() ? "" : " · " + String.join(" · ", parts);
    }

    /** 클릭 n건 (이동 m · 못 보냄 k) */
    private static String forwardLine(List<CountRow> rows, String kind) {
        long total = 0;
        long forwarded = 0;
        for (CountRow r : rows) {
            if (kind.equals(r.key())) {
                total += r.count();
                if ("FORWARDED".equals(r.label())) {
                    forwarded += r.count();
                }
            }
        }
        long failed = total - forwarded;
        return total + "건" + (failed > 0 ? " (⚠ 이동 못 함 " + failed + "건 — 접수관리 확인)" : "");
    }

    private static String joinTypes(Map<String, Long> m) {
        List<String> parts = new ArrayList<>();
        for (String type : List.of("POSTPAID", "PREPAID")) {
            if (m.getOrDefault(type, 0L) > 0) {
                parts.add(TYPE.get(type) + " " + m.get(type));
            }
        }
        return String.join(" · ", parts);
    }

    private static String signed(long n) {
        return n > 0 ? "+" + n : n == 0 ? "±0" : String.valueOf(n);
    }

    private static Map<String, Long> toMap(List<CountRow> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (CountRow r : rows) {
            m.merge(r.key(), r.count(), Long::sum);
        }
        return m;
    }

    /** 직전 리포트 숫자 {"date", "POSTPAID", "PREPAID"} — 없거나 깨졌으면 빈 값 (증감 생략) */
    static Map<String, Long> previous(String json) {
        Map<String, Long> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return m;
        }
        try {
            JsonNode n = JSON.readTree(json);
            for (String type : List.of("POSTPAID", "PREPAID")) {
                if (n.has(type)) {
                    m.put(type, n.get(type).asLong());
                }
            }
        } catch (RuntimeException e) {
            return Map.of();
        }
        return m;
    }

    private String check(String url) {
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 && res.body().contains("\"UP\"") ? "UP" : "HTTP " + res.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "응답 없음";
        } catch (Exception e) {
            return "응답 없음";
        }
    }
}
