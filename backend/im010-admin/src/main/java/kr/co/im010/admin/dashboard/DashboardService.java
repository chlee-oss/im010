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
                          List<Visit> byDevice, List<PageStat> pages, List<PlanStat> topPlans, List<PostStat> topPosts,
                          KeyView key) {
    }

    /**
     * 핵심 지표 (검색 유입 · 제휴사 이동 · 선불 비중).
     * searchSessions = 검색으로 들어온 세션, planOutbounds = 요금제 개통하기, internetOutbounds = 인터넷 상담 신청 이동
     * (이동 안내 화면 조회 − 요금제 개통하기), prepaid* = 선불 요금제의 조회 · 개통하기
     */
    public record KeyMetrics(long searchSessions, long planViews, long prepaidViews, long planOutbounds, long prepaidOutbounds,
                             long internetOutbounds) {
    }

    /** 요금제 구분(선불 · 후불)별 합계 */
    public record TypeStat(String planType, long views, long outbounds) {
    }

    public record KeyView(KeyMetrics current, KeyMetrics previous, List<Interest> searchEngines, List<Interest> searchLandings,
                          List<PartnerStat> partners, List<TypeStat> planTypes) {
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
        List<PlanStat> plans = mapper.plans(r.from(), r.to(), r.device(), null);
        List<PlanStat> prevPlans = mapper.plans(prev.from(), prev.to(), prev.device(), null);
        List<Visit> byRef = mapper.visitByRef(r.from(), r.to(), r.device());
        List<PageStat> pages = mapper.pages(r.from(), r.to(), r.device());
        Map<String, List<Interest>> dims = byDim(mapper.interests(r.from(), r.to(), r.device()));
        KeyView key = new KeyView(
                keyMetrics(plans, byRef, pages),
                keyMetrics(prevPlans, mapper.visitByRef(prev.from(), prev.to(), prev.device()),
                        mapper.pages(prev.from(), prev.to(), prev.device())),
                dims.getOrDefault("SEARCH_ENGINE", List.of()), dims.getOrDefault("SEARCH_LANDING", List.of()),
                partners(plans).stream().filter(p -> p.outbounds() > 0)
                        .sorted((a, b) -> Long.compare(b.outbounds(), a.outbounds())).toList(),
                planTypes(plans));
        return new Summary(r, settings(), totals(r, plans), totals(prev, prevPlans), mapper.daily(r.from(), r.to(), r.device()),
                byRef, mapper.visitByDevice(r.from(), r.to()), pages, plans.stream().limit(5).toList(),
                mapper.posts(r.from(), r.to(), r.device(), 5), key);
    }

    static KeyMetrics keyMetrics(List<PlanStat> plans, List<Visit> byRef, List<PageStat> pages) {
        long search = byRef.stream().filter(v -> "SEARCH".equals(v.key())).mapToLong(Visit::sessions).sum();
        long views = 0;
        long prepaidViews = 0;
        long outbounds = 0;
        long prepaidOutbounds = 0;
        for (PlanStat p : plans) {
            views += p.views();
            outbounds += p.outbounds();
            if ("PREPAID".equals(p.planType())) {
                prepaidViews += p.views();
                prepaidOutbounds += p.outbounds();
            }
        }
        long outPage = pages.stream().filter(p -> "OUT".equals(p.pageType())).mapToLong(PageStat::views).sum();
        return new KeyMetrics(search, views, prepaidViews, outbounds, prepaidOutbounds, Math.max(0, outPage - outbounds));
    }

    static List<TypeStat> planTypes(List<PlanStat> plans) {
        Map<String, long[]> sum = new LinkedHashMap<>();
        for (PlanStat p : plans) {
            long[] s = sum.computeIfAbsent(p.planType() == null ? "UNKNOWN" : p.planType(), k -> new long[2]);
            s[0] += p.views();
            s[1] += p.outbounds();
        }
        return sum.entrySet().stream().map(e -> new TypeStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.views(), a.views())).toList();
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

    private Totals totals(Range r, List<PlanStat> plans) {
        Visit v = mapper.visitTotal(r.from(), r.to(), r.device());
        long views = 0;
        long clicks = 0;
        long outbounds = 0;
        for (PlanStat p : plans) {
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
