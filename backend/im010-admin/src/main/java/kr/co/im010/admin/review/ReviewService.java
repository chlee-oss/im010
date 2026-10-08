package kr.co.im010.admin.review;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.plan.Fields;
import kr.co.im010.admin.plan.VersionDto;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.mapper.ReviewMapper;
import kr.co.im010.core.row.ItemValues;
import kr.co.im010.core.row.PlanAdminRow;
import kr.co.im010.core.row.ReviewItemRow;

/**
 * 요금제배치관리 (BA-02): 수집 리스트 확인 · 값 수정 · 점검 완료(→ 승인 요청) · 제외 · 판매 재개 요청.
 */
@Service
public class ReviewService {

    static final String PROGRAM = "BA-02";
    private static final Set<String> VIEWS = Set.of("PENDING", "REQUESTED", "UNCHANGED", "ENDED", "EXCLUDED", "ALL");
    private static final int PAGE_SIZE = 50;

    public record Page(LocalDate collectedOn, Map<String, Long> summary, int total, int page, List<ItemDto> items) {
    }

    /** current = 지금 승인돼 있는 값 (게시 중이거나 게시 대기 버전), 신규면 null */
    public record Detail(ItemDto item, VersionDto current, PlanAdminRow plan) {
    }

    public record BulkResult(List<Long> done, List<Skipped> skipped) {
    }

    public record Skipped(long id, String reason) {
    }

    private final ReviewMapper reviewMapper;
    private final PlanAdminMapper planMapper;
    private final AuditService audit;

    public ReviewService(ReviewMapper reviewMapper, PlanAdminMapper planMapper, AuditService audit) {
        this.reviewMapper = reviewMapper;
        this.planMapper = planMapper;
        this.audit = audit;
    }

    public Page list(String view, LocalDate collectedOn, String partnerCode, String urlType, int page) {
        String v = view == null ? "PENDING" : view;
        if (!VIEWS.contains(v)) {
            throw ApiException.badRequest("알 수 없는 보기: " + view);
        }
        LocalDate date = collectedOn != null ? collectedOn : reviewMapper.findLatestCollectedOn();
        if (date == null) {
            date = LocalDate.now();
        }
        int p = Math.max(page, 1);
        String partner = Texts.trim(partnerCode);
        String type = Texts.trim(urlType);
        List<ItemDto> items = reviewMapper.findItems(v, date, partner, type, PAGE_SIZE, (p - 1) * PAGE_SIZE)
                .stream().map(ItemDto::of).toList();
        return new Page(date, reviewMapper.countSummary(date), reviewMapper.countItems(v, date, partner, type), p, items);
    }

    public Detail detail(long id) {
        ReviewItemRow row = find(id);
        VersionDto current = null;
        PlanAdminRow plan = null;
        if (row.planId() != null) {
            plan = planMapper.findPlan(row.planId());
            current = row.urlType().equals("MONTHLY") ? null : VersionDto.of(planMapper.findLatestVersion(row.planId()));
        }
        return new Detail(ItemDto.of(row), current, plan);
    }

    /** "수정 후 점검": 수집값을 고친다. 원래 수집값 지문(value_hash)은 그대로 두어 다음 수집 때 같은 값이면 변경 없음으로 본다. */
    @Transactional
    public ItemDto update(long id, ItemValues values, String memo) {
        ReviewItemRow row = find(id);
        if (!row.status().equals("REVIEW_PENDING")) {
            throw ApiException.conflict("점검 대기 건만 고칠 수 있습니다");
        }
        ItemValues cleaned = Fields.clean(values);
        Set<String> edited = new LinkedHashSet<>(Texts.split(row.editedFields()));
        edited.addAll(Fields.diff(ItemDto.values(row), cleaned));
        reviewMapper.updateValues(id, cleaned, edited.toArray(String[]::new), Texts.trim(memo));
        audit.action(PROGRAM, "EDIT", "crawl_item " + id, String.join(",", edited));
        return ItemDto.of(find(id));
    }

    /**
     * 점검 완료 → 승인 요청. 요금제 버전에 꼭 필요한 항목이 비어 있으면 건너뛴다.
     * 여러 건을 한 번에 처리할 때는 ⚠ 경고가 있는 건도 건너뛴다 (BA-02: 이상치는 일괄 점검에서 제외).
     */
    @Transactional
    public BulkResult review(List<Long> ids) {
        List<Long> ok = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        boolean bulk = ids.size() > 1;
        for (ReviewItemRow row : reviewMapper.findItemsByIds(ids)) {
            if (!row.status().equals("REVIEW_PENDING")) {
                skipped.add(new Skipped(row.id(), "점검 대기 상태가 아닙니다"));
                continue;
            }
            if (!row.urlType().equals("MONTHLY")) {
                List<String> missing = Fields.missingForVersion(ItemDto.values(row));
                if (!missing.isEmpty()) {
                    skipped.add(new Skipped(row.id(), "비어 있는 항목: " + String.join(", ", missing)));
                    continue;
                }
            }
            if (bulk && !Texts.split(row.warnings()).isEmpty() && Texts.split(row.editedFields()).isEmpty()) {
                skipped.add(new Skipped(row.id(), "⚠ 경고가 있어 한 건씩 점검해야 합니다"));
                continue;
            }
            ok.add(row.id());
        }
        if (!ok.isEmpty()) {
            reviewMapper.markReviewed(ok, CurrentAdmin.get().loginId());
            audit.action(PROGRAM, "REVIEW", "crawl_item " + ok, null);
        }
        return new BulkResult(ok, skipped);
    }

    @Transactional
    public BulkResult exclude(List<Long> ids, String reason) {
        String r = Texts.trim(reason);
        if (r == null) {
            throw ApiException.badRequest("제외 사유를 입력해 주세요");
        }
        List<Long> ok = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (ReviewItemRow row : reviewMapper.findItemsByIds(ids)) {
            if (row.status().equals("REVIEW_PENDING") || row.status().equals("APPROVAL_REQUESTED")) {
                ok.add(row.id());
            } else {
                skipped.add(new Skipped(row.id(), "이미 처리된 건입니다"));
            }
        }
        if (!ok.isEmpty()) {
            reviewMapper.markExcluded(ok, CurrentAdmin.get().loginId(), r);
            audit.action(PROGRAM, "EXCLUDE", "crawl_item " + ok, r);
        }
        return new BulkResult(ok, skipped);
    }

    /** 자동 판매 종료가 잘못된 경우: [판매 재개] → 승인 요청 (승인하면 다시 게시 중). */
    @Transactional
    public ItemDto requestResume(long id) {
        if (reviewMapper.requestResume(id, CurrentAdmin.get().loginId()) == 0) {
            throw ApiException.conflict("자동 판매 종료된 요금제 건만 판매 재개를 요청할 수 있습니다");
        }
        audit.action(PROGRAM, "RESUME_REQUEST", "crawl_item " + id, null);
        return ItemDto.of(find(id));
    }

    /** [기존 요금제와 연결] 후보: 같은 제휴사 · 같은 유형의 요금제 (판매 종료 포함, 판매 종료가 앞) */
    public List<PlanAdminRow> linkCandidates(long id) {
        ReviewItemRow row = find(id);
        if (row.urlType().equals("MONTHLY")) {
            return List.of();
        }
        List<PlanAdminRow> ended = planMapper.findPlans(row.urlType(), row.partnerCode(), "ENDED", null, null, 200, 0);
        List<PlanAdminRow> all = planMapper.findPlans(row.urlType(), row.partnerCode(), null, null, null, 300, 0);
        List<PlanAdminRow> out = new ArrayList<>(ended);
        all.stream().filter(p -> !p.status().equals("ENDED")).forEach(out::add);
        return out;
    }

    /**
     * 이름(코드)만 바뀐 요금제: 신규 건을 기존 요금제의 변경 건으로 바꾼다. 승인하면 기존 요금제의 새 버전이 되고,
     * 판매 종료로 잡혔던 기존 요금제는 되살아나며, 제휴사 요금제 코드도 새 코드로 바뀐다.
     */
    @Transactional
    public ItemDto linkToPlan(long id, long planId) {
        ReviewItemRow row = find(id);
        PlanAdminRow plan = planMapper.findPlan(planId);
        if (plan == null || !plan.partnerCode().equals(row.partnerCode()) || !plan.planType().equals(row.urlType())) {
            throw ApiException.badRequest("같은 제휴사 · 같은 유형의 요금제만 연결할 수 있습니다");
        }
        if (row.partnerPlanCode() != null && !row.partnerPlanCode().equals(plan.partnerPlanCode())) {
            Long other = planMapper.findPlanIdByCode(row.partnerCode(), row.urlType(), row.partnerPlanCode());
            if (other != null && other != planId) {
                throw ApiException.conflict("이 제휴사 요금제 코드는 이미 요금제 #" + other + "에 쓰이고 있습니다");
            }
        }
        if (reviewMapper.linkToPlan(id, planId) == 0) {
            throw ApiException.conflict("점검 대기 중인 신규 건만 연결할 수 있습니다");
        }
        audit.action(PROGRAM, "LINK_PLAN", "crawl_item " + id, "plan " + planId);
        return ItemDto.of(find(id));
    }

    private ReviewItemRow find(long id) {
        ReviewItemRow row = reviewMapper.findItem(id);
        if (row == null) {
            throw ApiException.notFound("수집 건");
        }
        return row;
    }
}
