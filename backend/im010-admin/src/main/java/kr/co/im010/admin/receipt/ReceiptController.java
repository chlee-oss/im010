package kr.co.im010.admin.receipt;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Csv;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.ReceiptMapper;
import kr.co.im010.core.row.CountRow;
import kr.co.im010.core.row.ReceiptRow;

/**
 * 접수관리: RC-01 알뜰폰접수신청 (/receipts/plans — 개통하기) · RC-02 인터넷접수신청 (/receipts/internet — 상담 신청 이동).
 * 개인정보는 없다. 접수번호는 M-{id} · I-{id}. 목록 상단 합계 · 제휴사별 건수 · 엑셀(CSV) 다운로드 (다운로드 권한, 이력 기록).
 */
@RestController
@RequestMapping("/admin/api/receipts")
public class ReceiptController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PAGE = 100;
    private static final int DOWNLOAD_MAX = 100_000;
    private static final Map<String, String> KIND = Map.of("plans", "PLAN", "internet", "INTERNET");
    private static final Map<String, String> PROGRAM = Map.of("plans", "RC-01", "internet", "RC-02");
    private static final Map<String, String> RESULT = Map.of("FORWARDED", "이동 완료", "ENDED", "판매 종료",
            "NO_URL", "URL 없음", "HIDDEN", "비노출");
    private static final Map<String, String> CATEGORY = Map.of("POSTPAID", "후불", "PREPAID", "선불", "SINGLE", "단독",
            "BUNDLE", "결합");

    public record Page(long today, long periodTotal, List<CountRow> byPartner, int total, int page, List<ReceiptRow> items) {
    }

    private final ReceiptMapper mapper;
    private final AuditService audit;

    public ReceiptController(ReceiptMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @GetMapping("/{kind}")
    public Page list(@PathVariable String kind,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                     @RequestParam(required = false) String partner, @RequestParam(required = false) String category,
                     @RequestParam(required = false) String fromPage, @RequestParam(required = false) String result,
                     @RequestParam(defaultValue = "1") int page) {
        String k = kind(kind, Action.VIEW);
        OffsetDateTime[] r = range(from, to);
        int p = Math.max(page, 1);
        String pt = Texts.trim(partner);
        String c = Texts.trim(category);
        String fp = Texts.trim(fromPage);
        String rs = Texts.trim(result);
        return new Page(
                mapper.countSince(k, LocalDate.now(KST).atStartOfDay(KST).toOffsetDateTime()),
                mapper.countReceipts(k, r[0], r[1], null, null, null, null),
                mapper.countByPartner(k, r[0], r[1]),
                mapper.countReceipts(k, r[0], r[1], pt, c, fp, rs), p,
                mapper.findReceipts(k, r[0], r[1], pt, c, fp, rs, PAGE, (p - 1) * PAGE));
    }

    @GetMapping("/{kind}.csv")
    public void download(@PathVariable String kind,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) String partner, @RequestParam(required = false) String category,
                         @RequestParam(required = false) String fromPage, @RequestParam(required = false) String result,
                         HttpServletResponse response) throws IOException {
        String k = kind(kind, Action.DOWNLOAD);
        OffsetDateTime[] r = range(from, to);
        List<ReceiptRow> rows = mapper.findReceipts(k, r[0], r[1], Texts.trim(partner), Texts.trim(category),
                Texts.trim(fromPage), Texts.trim(result), DOWNLOAD_MAX, 0);
        boolean plan = k.equals("PLAN");
        String name = (plan ? "알뜰폰접수신청_" : "인터넷접수신청_") + r[0].toLocalDate() + "_" + r[1].minusDays(1).toLocalDate() + ".csv";
        audit.action(PROGRAM.get(kind), "DOWNLOAD", name, rows.size() + "건");
        List<String> header = plan
                ? List.of("접수번호", "접수 일시", "제휴사", "구분", "요금제", "버전", "유입 화면", "이동 결과", "이동 URL")
                : List.of("접수번호", "이동 일시", "통신사", "유형", "상품", "제휴업체", "유입 화면", "이동 결과", "이동 URL");
        Csv.write(response, name, header, rows.stream().map(x -> plan
                ? List.<Object>of("M-" + x.id(), ts(x), nz(x.partnerName()), cat(x), nz(x.targetName()),
                        x.versionNo() == null ? "" : "v" + x.versionNo(), nz(x.fromPage()), RESULT.getOrDefault(x.result(), x.result()),
                        nz(x.targetUrl()))
                : List.<Object>of("I-" + x.id(), ts(x), nz(x.carrier()), cat(x), nz(x.targetName()), nz(x.partnerName()),
                        nz(x.fromPage()), RESULT.getOrDefault(x.result(), x.result()), nz(x.targetUrl()))).toList());
    }

    private static String kind(String kind, Action action) {
        String k = KIND.get(kind);
        if (k == null) {
            throw ApiException.notFound("접수 화면");
        }
        CurrentAdmin.require(PROGRAM.get(kind), action);
        return k;
    }

    /** 기본 최근 7일, 최대 1년 */
    private static OffsetDateTime[] range(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now(KST);
        LocalDate f = from != null ? from : t.minusDays(6);
        if (f.isAfter(t) || f.isBefore(t.minusYears(1))) {
            throw ApiException.badRequest("기간은 1년 이내로 지정해 주세요");
        }
        return new OffsetDateTime[] {f.atStartOfDay(KST).toOffsetDateTime(), t.plusDays(1).atStartOfDay(KST).toOffsetDateTime()};
    }

    private static String ts(ReceiptRow r) {
        return TS.format(r.createdAt().atZoneSameInstant(KST));
    }

    private static String cat(ReceiptRow r) {
        return r.category() == null ? "" : CATEGORY.getOrDefault(r.category(), r.category());
    }

    private static Object nz(Object o) {
        return o == null ? "" : o;
    }
}
