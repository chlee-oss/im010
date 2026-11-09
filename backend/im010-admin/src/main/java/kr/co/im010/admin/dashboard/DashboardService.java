package kr.co.im010.admin.dashboard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.core.mapper.AnalyticsMapper;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.AnalyticsRows.Daily;
import kr.co.im010.core.row.AnalyticsRows.Interest;
import kr.co.im010.core.row.AnalyticsRows.PageStat;
import kr.co.im010.core.row.AnalyticsRows.PlanStat;
import kr.co.im010.core.row.AnalyticsRows.PostStat;
import kr.co.im010.core.row.AnalyticsRows.Visit;

/**
 * 대시보드: DS-01 요약 · DS-02 관심사 · DS-03 콘텐츠 효과. batch 가 만든 일별 집계(stat_daily_*)만 읽는다.
 * 기간은 한국 날짜 기준 (기본 최근 7일, 최대 1년), 기기는 ALL · PC · MO.
 */
@Service
public class DashboardService {

    public static final String SUMMARY = "DS-01";
    public static final String INTEREST = "DS-02";
    public static final String CONTENT = "DS-03";

    static final String ENABLED = "analytics_enabled";
    static final String AGGREGATED_AT = "analytics_aggregated_at";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> DEVICES = Set.of("ALL", "PC", "MO");

    /** 조회 조건 */
    public record Range(LocalDate from, LocalDate to, String device) {
        long days() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    /** 수집 설정 · 마지막 집계 시각 */
    public record Settings(boolean enabled, String aggregatedAt) {
    }

    /** 기간 합계 (방문 · 요금제) */
    public record Totals(long visitors, long newVisitors, long sessions, long pageViews, long planViews, long planClicks,
                         long outbounds) {
    }

    public record Summary(Range range, Settings settings, Totals current, Totals previous, List<Daily> daily, List<Visit> byRef,
                          List<Visit> byDevice, List<PageStat> pages, List<PlanStat> topPlans, List<PostStat> topPosts) {
    }

    /** 제휴사 합계 */
    public record PartnerStat(String partnerCode, String partnerName, long views, long clicks, long outbounds) {
    }

    public record InterestView(Range range, Settings settings, Map<String, List<Interest>> dims, List<PlanStat> plans,
                               List<PartnerStat> partners) {
    }

    /** 카테고리 합계 */
    public record CategoryStat(String categoryName, long posts, long views, long completes, long planClicks) {
    }

    public record ContentView(Range range, Settings settings, List<PostStat> posts, List<CategoryStat> categories,
                              Map<String, List<Interest>> internet) {
    }

    private final AnalyticsMapper mapper;
    private final SettingsMapper settings;
    private final AuditService audit;

    public DashboardService(AnalyticsMapper mapper, SettingsMapper settings, AuditService audit) {
        this.mapper = mapper;
        this.settings = settings;
        this.audit = audit;
    }

    public static Range range(LocalDate from, LocalDate to, String device) {
        LocalDate t = to != null ? to : LocalDate.now(KST);
        LocalDate f = from != null ? from : t.minusDays(6);
        if (f.isAfter(t) || f.isBefore(t.minusYears(1))) {
            throw ApiException.badRequest("기간은 1년 이내로 지정해 주세요");
        }
        String d = device == null || device.isBlank() ? "ALL" : device;
        if (!DEVICES.contains(d)) {
            throw ApiException.badRequest("기기는 ALL · PC · MO 중 하나입니다");
        }
        return new Range(f, t, d);
    }

    public Settings settings() {
        String at = settings.findSetting(AGGREGATED_AT);
        return new Settings("true".equals(settings.findSetting(ENABLED)), at == null || at.isBlank() ? null : at);
    }

    public Settings setEnabled(boolean enabled) {
        settings.updateSetting(ENABLED, String.valueOf(enabled), CurrentAdmin.get().loginId());
        audit.action(SUMMARY, "ANALYTICS_" + (enabled ? "ON" : "OFF"), "이용 통계 수집", enabled ? "켬" : "끔");
        return settings();
    }

    public Summary summary(Range r) {
        Range prev = new Range(r.from().minusDays(r.days()), r.from().minusDays(1), r.device());
        return new Summary(r, settings(), totals(r), totals(prev), mapper.daily(r.from(), r.to(), r.device()),
                mapper.visitByRef(r.from(), r.to(), r.device()), mapper.visitByDevice(r.from(), r.to()),
                mapper.pages(r.from(), r.to(), r.device()), mapper.plans(r.from(), r.to(), r.device(), 5),
                mapper.posts(r.from(), r.to(), r.device(), 5));
    }

    public InterestView interest(Range r) {
        List<PlanStat> plans = mapper.plans(r.from(), r.to(), r.device(), null);
        return new InterestView(r, settings(), byDim(mapper.interests(r.from(), r.to(), r.device())), plans, partners(plans));
    }

    public ContentView content(Range r) {
        List<PostStat> posts = mapper.posts(r.from(), r.to(), r.device(), null);
        Map<String, List<Interest>> internet = new LinkedHashMap<>();
        byDim(mapper.interests(r.from(), r.to(), r.device())).forEach((dim, list) -> {
            if (dim.startsWith("INTERNET_")) {
                internet.put(dim, list);
            }
        });
        return new ContentView(r, settings(), posts, categories(posts), internet);
    }

    public List<PlanStat> plans(Range r) {
        return mapper.plans(r.from(), r.to(), r.device(), null);
    }

    public List<PostStat> posts(Range r) {
        return mapper.posts(r.from(), r.to(), r.device(), null);
    }

    public List<Daily> daily(Range r) {
        return mapper.daily(r.from(), r.to(), r.device());
    }

    private Totals totals(Range r) {
        Visit v = mapper.visitTotal(r.from(), r.to(), r.device());
        long views = 0;
        long clicks = 0;
        long outbounds = 0;
        for (PlanStat p : mapper.plans(r.from(), r.to(), r.device(), null)) {
            views += p.views();
            clicks += p.clicks();
            outbounds += p.outbounds();
        }
        return new Totals(v.visitors(), v.newVisitors(), v.sessions(), v.pageViews(), views, clicks, outbounds);
    }

    static Map<String, List<Interest>> byDim(List<Interest> rows) {
        Map<String, List<Interest>> out = new LinkedHashMap<>();
        for (Interest i : rows) {
            out.computeIfAbsent(i.dim(), k -> new ArrayList<>()).add(i);
        }
        return out;
    }

    static List<PartnerStat> partners(List<PlanStat> plans) {
        Map<String, long[]> sum = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (PlanStat p : plans) {
            String code = p.partnerCode() == null ? "" : p.partnerCode();
            names.putIfAbsent(code, p.partnerName() == null ? "(알 수 없음)" : p.partnerName());
            long[] s = sum.computeIfAbsent(code, k -> new long[3]);
            s[0] += p.views();
            s[1] += p.clicks();
            s[2] += p.outbounds();
        }
        return sum.entrySet().stream()
                .map(e -> new PartnerStat(e.getKey(), names.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted((a, b) -> Long.compare(b.views(), a.views()))
                .toList();
    }

    static List<CategoryStat> categories(List<PostStat> posts) {
        Map<String, long[]> sum = new LinkedHashMap<>();
        for (PostStat p : posts) {
            long[] s = sum.computeIfAbsent(p.categoryName() == null ? "(없음)" : p.categoryName(), k -> new long[4]);
            s[0]++;
            s[1] += p.views();
            s[2] += p.completes();
            s[3] += p.planClicks();
        }
        return sum.entrySet().stream()
                .map(e -> new CategoryStat(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .sorted((a, b) -> Long.compare(b.views(), a.views()))
                .toList();
    }
}
