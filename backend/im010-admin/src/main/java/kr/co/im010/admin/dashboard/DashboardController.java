package kr.co.im010.admin.dashboard;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.admin.dashboard.DashboardService.ContentView;
import kr.co.im010.admin.dashboard.DashboardService.InterestView;
import kr.co.im010.admin.dashboard.DashboardService.Range;
import kr.co.im010.admin.dashboard.DashboardService.Settings;
import kr.co.im010.admin.dashboard.DashboardService.Summary;
import kr.co.im010.admin.web.Csv;
import kr.co.im010.core.row.AnalyticsRows.PlanStat;
import kr.co.im010.core.row.AnalyticsRows.PostStat;

/**
 * 대시보드 (DS-01 요약 · DS-02 관심사 · DS-03 콘텐츠 효과). 화면마다 권한관리(ST-03)에서 그룹별 조회 · 다운로드를 정한다.
 * 수집 켜기 · 끄기는 DS-01 수정 권한.
 */
@RestController
@RequestMapping("/admin/api/dashboard")
public class DashboardController {

    private static final Map<String, String> PLAN_TYPE = Map.of("POSTPAID", "후불", "PREPAID", "선불");
    private static final Map<String, String> DEVICE = Map.of("ALL", "전체", "PC", "PC", "MO", "모바일");

    public record EnabledBody(boolean enabled) {
    }

    private final DashboardService service;
    private final AuditService audit;

    public DashboardController(DashboardService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/settings")
    @RequiresPermission(program = DashboardService.SUMMARY, action = Action.VIEW)
    public Settings settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    @RequiresPermission(program = DashboardService.SUMMARY, action = Action.EDIT)
    public Settings setEnabled(@RequestBody EnabledBody body) {
        return service.setEnabled(body.enabled());
    }

    @GetMapping("/summary")
    @RequiresPermission(program = DashboardService.SUMMARY, action = Action.VIEW)
    public Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) String device) {
        return service.summary(DashboardService.range(from, to, device));
    }

    @GetMapping("/interest")
    @RequiresPermission(program = DashboardService.INTEREST, action = Action.VIEW)
    public InterestView interest(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) String device) {
        return service.interest(DashboardService.range(from, to, device));
    }

    @GetMapping("/content")
    @RequiresPermission(program = DashboardService.CONTENT, action = Action.VIEW)
    public ContentView content(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) String device) {
        return service.content(DashboardService.range(from, to, device));
    }

    /** 요약: 일별 추이 */
    @GetMapping("/summary.csv")
    @RequiresPermission(program = DashboardService.SUMMARY, action = Action.DOWNLOAD)
    public void summaryCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) String device, HttpServletResponse response) throws IOException {
        Range r = DashboardService.range(from, to, device);
        var rows = service.daily(r);
        String name = fileName("대시보드_요약", r);
        audit.action(DashboardService.SUMMARY, "DOWNLOAD", name, rows.size() + "일");
        Csv.write(response, name, List.of("날짜", "기기", "방문자", "페이지뷰", "요금제 조회", "개통하기"),
                rows.stream().map(d -> List.<Object>of(d.day().toString(), DEVICE.get(r.device()), d.visitors(), d.pageViews(),
                        d.planViews(), d.outbounds())).toList());
    }

    /** 관심사: 요금제별 조회 · 클릭 · 개통하기 */
    @GetMapping("/interest.csv")
    @RequiresPermission(program = DashboardService.INTEREST, action = Action.DOWNLOAD)
    public void interestCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) String device, HttpServletResponse response) throws IOException {
        Range r = DashboardService.range(from, to, device);
        List<PlanStat> rows = service.plans(r);
        String name = fileName("대시보드_요금제", r);
        audit.action(DashboardService.INTEREST, "DOWNLOAD", name, rows.size() + "건");
        Csv.write(response, name,
                List.of("요금제 ID", "제휴사", "요금제", "구분", "망", "세대", "요금", "조회", "조회 방문자", "클릭", "개통하기", "개통 전환율(%)"),
                rows.stream().map(p -> List.<Object>of(p.planId(), nz(p.partnerName()), p.name(),
                        p.planType() == null ? "" : PLAN_TYPE.getOrDefault(p.planType(), p.planType()), nz(p.network()),
                        nz(p.generation()),
                        p.price() == null ? "" : p.price(), p.views(), p.visitors(), p.clicks(), p.outbounds(),
                        rate(p.outbounds(), p.views()))).toList());
    }

    /** 콘텐츠: 알뜰AI 글별 */
    @GetMapping("/content.csv")
    @RequiresPermission(program = DashboardService.CONTENT, action = Action.DOWNLOAD)
    public void contentCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) String device, HttpServletResponse response) throws IOException {
        Range r = DashboardService.range(from, to, device);
        List<PostStat> rows = service.posts(r);
        String name = fileName("대시보드_콘텐츠", r);
        audit.action(DashboardService.CONTENT, "DOWNLOAD", name, rows.size() + "건");
        Csv.write(response, name,
                List.of("글 ID", "카테고리", "제목", "조회", "조회 방문자", "끝까지 읽음", "완독률(%)", "평균 머문 시간(초)", "요금제로 이동",
                        "이동률(%)"),
                rows.stream().map(p -> List.<Object>of(p.postId(), nz(p.categoryName()), p.title(), p.views(), p.visitors(),
                        p.completes(), rate(p.completes(), p.views()),
                        p.engageCount() == 0 ? "" : Math.round(p.engageMs() / 1000.0 / p.engageCount()), p.planClicks(),
                        rate(p.planClicks(), p.views()))).toList());
    }

    private static String fileName(String prefix, Range r) {
        return prefix + "_" + r.from() + "_" + r.to() + (r.device().equals("ALL") ? "" : "_" + r.device()) + ".csv";
    }

    static Object rate(long part, long whole) {
        return whole == 0 ? "" : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static Object nz(Object o) {
        return o == null ? "" : o;
    }
}
