package kr.co.im010.admin.partner;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.PartnerAdminMapper;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.parse.PageFetcher;
import kr.co.im010.core.parse.ParsedPage;
import kr.co.im010.core.parse.ParsedPlan;
import kr.co.im010.core.parse.RatePlanParser;
import kr.co.im010.core.parse.Urls;
import kr.co.im010.core.row.CollectUrlRow;
import kr.co.im010.core.row.CrawlRunAdminRow;
import kr.co.im010.core.row.PartnerAdminRow;
import kr.co.im010.core.row.PartnerTabRow;

/**
 * 제휴사관리 (PA-01 [알뜰폰]): 제휴사 정보 · 수집 URL(유형별 탭마다) · 사이트 탭 확인 · [테스트].
 * 스케줄관리 (BA-01): 수집 일정 · 즉시 실행 · 실행 이력.
 */
@Service
public class PartnerAdminService {

    public static final String PROGRAM = "PA-01";
    public static final String SCHEDULE_PROGRAM = "BA-01";

    private static final Set<String> URL_TYPES = Set.of("POSTPAID", "PREPAID", "MONTHLY");
    private static final Set<String> DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final Pattern CODE = Pattern.compile("^[a-z0-9]{2,10}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public record PartnerRequest(String code, String name, String chipBg, String chipFg, String homepageUrl,
                                 boolean exposed, int sortOrder) {
    }

    public record UrlRequest(String urlType, String url, String label, int sortOrder) {
    }

    /** registered = 수집 URL 로 등록된 탭 */
    public record Detail(PartnerAdminRow partner, List<CollectUrlRow> urls, List<PartnerTabRow> tabs) {
    }

    public record TestResult(int count, int missingRequired, List<ParsedPlan> preview, List<ParsedPage.Tab> tabs) {
    }

    public record ScheduleRequest(boolean enabled, String days, String runTime) {
    }

    private final PartnerAdminMapper partnerMapper;
    private final PlanAdminMapper planMapper;
    private final PageFetcher fetcher;
    private final RatePlanParser parser;
    private final AuditService audit;

    public PartnerAdminService(PartnerAdminMapper partnerMapper, PlanAdminMapper planMapper, PageFetcher fetcher,
                               RatePlanParser parser, AuditService audit) {
        this.partnerMapper = partnerMapper;
        this.planMapper = planMapper;
        this.fetcher = fetcher;
        this.parser = parser;
        this.audit = audit;
    }

    public List<PartnerAdminRow> list() {
        return partnerMapper.findPartners();
    }

    public Detail detail(String code) {
        PartnerAdminRow partner = find(code);
        return new Detail(partner, partnerMapper.findUrls(code), partnerMapper.findTabs(code));
    }

    @Transactional
    public Detail create(PartnerRequest req) {
        String code = Texts.trim(req.code());
        if (code == null || !CODE.matcher(code).matches()) {
            throw ApiException.badRequest("코드는 영문 소문자 · 숫자 2 ~ 10자여야 합니다");
        }
        if (partnerMapper.findPartner(code) != null) {
            throw ApiException.conflict("이미 있는 코드입니다");
        }
        PartnerRequest r = validate(req);
        partnerMapper.insertPartner(code, r.name(), r.chipBg(), r.chipFg(), r.homepageUrl(), r.exposed(), r.sortOrder());
        partnerMapper.upsertSchedule(code, false, "DAILY", LocalTime.of(4, 0));   // 수집 URL 등록 후 켠다
        audit.action(PROGRAM, "PARTNER_CREATE", code, r.name());
        return detail(code);
    }

    @Transactional
    public Detail update(String code, PartnerRequest req) {
        find(code);
        PartnerRequest r = validate(req);
        partnerMapper.updatePartner(code, r.name(), r.chipBg(), r.chipFg(), r.homepageUrl(), r.exposed(), r.sortOrder());
        audit.action(PROGRAM, "PARTNER_UPDATE", code, r.name() + (r.exposed() ? "" : " (비노출)"));
        return detail(code);
    }

    @Transactional
    public Detail addUrl(String code, UrlRequest req) {
        find(code);
        UrlRequest r = validateUrl(req, null);
        partnerMapper.insertUrl(code, r.urlType(), r.url(), r.label(), r.sortOrder());
        partnerMapper.deleteTabByUrl(code, r.url());
        audit.action(PROGRAM, "URL_ADD", code + " " + r.urlType(), r.url());
        return detail(code);
    }

    @Transactional
    public Detail updateUrl(String code, long id, UrlRequest req) {
        CollectUrlRow current = findUrl(code, id);
        UrlRequest r = validateUrl(req, id);
        if (!current.urlType().equals(r.urlType()) && partnerMapper.countUrls(code, current.urlType()) == 1) {
            throw ApiException.conflict("이 유형의 마지막 URL 은 유형을 바꿀 수 없습니다. 삭제 후 새로 등록해 주세요");
        }
        partnerMapper.updateUrl(id, r.urlType(), r.url(), r.label(), r.sortOrder());
        partnerMapper.deleteTabByUrl(code, r.url());
        audit.action(PROGRAM, "URL_UPDATE", code + " " + r.urlType(), current.url() + " -> " + r.url());
        return detail(code);
    }

    /**
     * URL 삭제. 그 유형의 마지막 URL 이면 수집이 멈추므로 게시 중 요금제를 비노출로 할지 고른다 (기본 비노출, 판매 종료 아님).
     */
    @Transactional
    public Detail deleteUrl(String code, long id, boolean hidePlans) {
        CollectUrlRow url = findUrl(code, id);
        boolean last = partnerMapper.countUrls(code, url.urlType()) == 1;
        partnerMapper.deleteUrl(id);
        int hidden = 0;
        if (last && hidePlans && !url.urlType().equals("MONTHLY")) {
            hidden = planMapper.hidePublished(code, url.urlType());
        }
        audit.action(PROGRAM, "URL_DELETE", code + " " + url.urlType(), url.url() + (hidden > 0 ? " · 비노출 " + hidden + "건" : ""));
        return detail(code);
    }

    /** [테스트]: 한 번 받아 파싱해 개수 · 앞 5건 · 탭을 보여 준다. 저장하지 않는다. */
    public TestResult test(String code, String url, String label) {
        find(code);
        String u = Urls.clean(Texts.requireHttpsUrl(url, "수집 URL"));
        try {
            ParsedPage page = parser.parse(fetcher.fetch(u), u, Texts.trim(label));
            int missing = (int) page.plans().stream().filter(ParsedPlan::missingRequired).count();
            return new TestResult(page.plans().size(), missing, page.plans().stream().limit(5).toList(), page.tabs());
        } catch (IOException e) {
            throw ApiException.badRequest("페이지를 받지 못했습니다: " + e.getMessage());
        }
    }

    @Transactional
    public Detail updateTab(String code, long id, String status) {
        PartnerTabRow tab = partnerMapper.findTab(id);
        if (tab == null || !tab.partnerCode().equals(code)) {
            throw ApiException.notFound("사이트 탭");
        }
        if (!"IGNORED".equals(status) && !"NEW".equals(status)) {
            throw ApiException.badRequest("상태는 IGNORED 또는 NEW 여야 합니다");
        }
        partnerMapper.updateTabStatus(id, status);
        audit.action(PROGRAM, "TAB_" + status, code, tab.url());
        return detail(code);
    }

    // ---------- BA-01 ----------

    @Transactional
    public PartnerAdminRow updateSchedule(String code, ScheduleRequest req) {
        find(code);
        String days = req.days() == null ? "DAILY" : req.days().trim().toUpperCase();
        if (!days.equals("DAILY")) {
            List<String> list = List.of(days.split(","));
            if (list.isEmpty() || !DAYS.containsAll(list) || Set.copyOf(list).size() != list.size()) {
                throw ApiException.badRequest("요일은 DAILY 또는 MON,TUE,... 형식이어야 합니다");
            }
        }
        LocalTime time;
        try {
            time = LocalTime.parse(req.runTime());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("시각은 HH:mm 형식이어야 합니다");
        }
        if (time.getMinute() % 10 != 0 || time.getSecond() != 0) {
            throw ApiException.badRequest("시각은 10분 단위로 지정해 주세요");
        }
        partnerMapper.upsertSchedule(code, req.enabled(), days, time);
        audit.action(SCHEDULE_PROGRAM, "SCHEDULE_UPDATE", code, (req.enabled() ? "ON " : "OFF ") + days + " " + time);
        return find(code);
    }

    /** [즉시 실행]: 수집 작업을 큐에 넣는다 (배치가 1분 안에 실행). 이미 대기 · 실행 중이면 넣지 않는다. */
    @Transactional
    public PartnerAdminRow runNow(String code) {
        PartnerAdminRow partner = find(code);
        if (partner.postpaidUrls() + partner.prepaidUrls() + partner.monthlyUrls() == 0) {
            throw ApiException.conflict("등록된 수집 URL 이 없습니다");
        }
        if (partnerMapper.existsWaitingJob(code)) {
            throw ApiException.conflict("이미 대기 중이거나 실행 중인 수집이 있습니다");
        }
        partnerMapper.insertManualJob(code, LocalDate.now(KST), CurrentAdmin.get().loginId());
        audit.action(SCHEDULE_PROGRAM, "RUN_NOW", code, null);
        return find(code);
    }

    public List<CrawlRunAdminRow> runs(LocalDate from, LocalDate to, String partner, String urlType, String result) {
        LocalDate t = to != null ? to : LocalDate.now(KST);
        LocalDate f = from != null ? from : t.minusDays(6);
        if (f.isAfter(t) || f.isBefore(t.minusDays(92))) {
            throw ApiException.badRequest("기간은 92일 이내로 지정해 주세요");
        }
        return partnerMapper.findRuns(f, t, Texts.trim(partner), Texts.trim(urlType), Texts.trim(result), 500);
    }

    private PartnerRequest validate(PartnerRequest r) {
        String name = Texts.trim(r.name());
        if (name == null || name.length() > 50) {
            throw ApiException.badRequest("브랜드명은 1 ~ 50자여야 합니다");
        }
        if (r.chipBg() == null || !COLOR.matcher(r.chipBg()).matches() || r.chipFg() == null || !COLOR.matcher(r.chipFg()).matches()) {
            throw ApiException.badRequest("칩 색상은 #RRGGBB 형식이어야 합니다");
        }
        String homepage = Texts.trim(r.homepageUrl()) == null ? null : Texts.requireHttpsUrl(r.homepageUrl(), "홈페이지");
        return new PartnerRequest(r.code(), name, r.chipBg().toUpperCase(), r.chipFg().toUpperCase(), homepage,
                r.exposed(), r.sortOrder());
    }

    private UrlRequest validateUrl(UrlRequest r, Long excludeId) {
        if (r.urlType() == null || !URL_TYPES.contains(r.urlType())) {
            throw ApiException.badRequest("유형은 후불 · 선불 · 이달의 요금제 중 하나여야 합니다");
        }
        String url = Urls.clean(Texts.requireHttpsUrl(r.url(), "수집 URL"));
        CollectUrlRow conflict = partnerMapper.findConflictingUrl(r.urlType(), url, excludeId);
        if (conflict != null) {
            throw ApiException.conflict(conflict.urlType().equals(r.urlType())
                    ? "이미 등록된 URL 입니다"
                    : "후불과 선불은 다른 URL 이어야 합니다");
        }
        String label = Texts.trim(r.label());
        if (label != null && label.length() > 50) {
            throw ApiException.badRequest("탭 이름은 50자 이하여야 합니다");
        }
        return new UrlRequest(r.urlType(), url, label, r.sortOrder());
    }

    private CollectUrlRow findUrl(String code, long id) {
        CollectUrlRow url = partnerMapper.findUrl(id);
        if (url == null || !url.partnerCode().equals(code)) {
            throw ApiException.notFound("수집 URL");
        }
        return url;
    }

    private PartnerAdminRow find(String code) {
        PartnerAdminRow p = partnerMapper.findPartner(code);
        if (p == null) {
            throw ApiException.notFound("제휴사");
        }
        return p;
    }
}
