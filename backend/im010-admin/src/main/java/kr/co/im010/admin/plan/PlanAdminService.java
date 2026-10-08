package kr.co.im010.admin.plan;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.config.AdminProperties;
import kr.co.im010.admin.review.ReviewService;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.row.CalendarRow;
import kr.co.im010.core.row.ItemValues;
import kr.co.im010.core.row.NewPlanVersion;
import kr.co.im010.core.row.PlanAdminRow;
import kr.co.im010.core.row.PlanVersionRow;

/**
 * 요금제관리 (PR-01): 승인으로 이관된 요금제의 보완 입력 · 개통 URL · 게시 예약 · 즉시 게시 · 예약 취소 · 비노출 · 롤백.
 * 게시 중인 요금제를 고치면 새 버전(게시 대기)이 생기고, 게시할 때까지 기존 버전이 계속 노출된다.
 */
@Service
public class PlanAdminService {

    public static final String PROGRAM = "PR-01";
    private static final int PAGE_SIZE = 50;
    private static final int MAX_TAGS = 6;

    /** state = PUBLISHED · DRAFT · SCHEDULED · ENDED · HIDDEN (화면 상태), missing = 게시 전 필수 항목 중 빈 것 */
    public record ListItem(PlanAdminRow plan, String state, List<String> missing) {
    }

    public record Page(int total, int page, List<ListItem> items) {
    }

    public record Detail(PlanAdminRow plan, String state, VersionDto published, VersionDto draft,
                         List<VersionDto> versions, List<String> missing, boolean canRollback) {
    }

    public record DraftRequest(ItemValues values, Integer validDays, List<String> tags) {
    }

    private final PlanAdminMapper planMapper;
    private final AuditService audit;
    private final AdminProperties props;

    public PlanAdminService(PlanAdminMapper planMapper, AuditService audit, AdminProperties props) {
        this.planMapper = planMapper;
        this.audit = audit;
        this.props = props;
    }

    public Page list(String planType, String partner, String state, String network, String q, int page) {
        String type = "PREPAID".equals(planType) ? "PREPAID" : "POSTPAID";
        int p = Math.max(page, 1);
        String partnerCode = Texts.trim(partner);
        String st = Texts.trim(state);
        String net = Texts.trim(network);
        String query = Texts.trim(q);
        List<ListItem> items = planMapper.findPlans(type, partnerCode, st, net, query, PAGE_SIZE, (p - 1) * PAGE_SIZE)
                .stream().map(this::listItem).toList();
        return new Page(planMapper.countPlans(type, partnerCode, st, net, query), p, items);
    }

    public Detail detail(long id) {
        PlanAdminRow plan = find(id);
        PlanVersionRow published = plan.publishedVersionId() == null ? null : planMapper.findVersion(plan.publishedVersionId());
        PlanVersionRow draft = planMapper.findDraft(id);
        List<String> missing = draft == null ? List.of() : PublishRules.missing(plan.planType(), draft, plan.activationUrl());
        return new Detail(plan, state(plan), VersionDto.of(published), VersionDto.of(draft),
                planMapper.findVersions(id).stream().map(VersionDto::of).toList(), missing, rollbackTarget(plan) != null);
    }

    /** 보완 입력 · 값 수정. 대기 버전이 있으면 그 버전을, 없으면 게시 중 버전을 복사한 새 버전을 고친다. */
    @Transactional
    public Detail editDraft(long id, DraftRequest req) {
        PlanAdminRow plan = find(id);
        if (plan.status().equals("ENDED")) {
            throw ApiException.conflict("판매 종료된 요금제는 고칠 수 없습니다");
        }
        PlanVersionRow draft = planMapper.findDraft(id);
        PlanVersionRow base = draft != null ? draft : planMapper.findLatestVersion(id);
        if (base == null) {
            throw ApiException.notFound("요금제 버전");
        }
        ItemValues values = Fields.clean(req.values());
        List<String> missing = Fields.missingForVersion(values);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("비어 있는 항목: " + String.join(", ", missing));
        }
        if (req.validDays() != null && (req.validDays() < 1 || req.validDays() > 3650)) {
            throw ApiException.badRequest("사용 기간은 1 ~ 3650일이어야 합니다");
        }
        List<String> tags = tags(req.tags());
        boolean prepaid = plan.planType().equals("PREPAID");

        List<String> changed = Fields.diff(values(base), values);
        Set<String> supplemented = new LinkedHashSet<>(Texts.split(base.supplementedFields()));
        supplemented.addAll(changed);
        if (!Objects.equals(base.validDays(), req.validDays())) {
            supplemented.add("validDays");
        }
        if (!Texts.split(base.tags()).equals(tags)) {
            supplemented.add("tags");
        }
        Integer monthly = prepaid ? null : values.price();
        Integer charge = prepaid ? values.price() : null;
        if (draft != null) {
            planMapper.updateDraftValues(draft.id(), values, monthly, charge, req.validDays(),
                    tags.toArray(String[]::new), supplemented.toArray(String[]::new));
        } else {
            planMapper.insertVersion(new NewPlanVersion(id, planMapper.nextVersionNo(id), values.name(), values.dataText(),
                    values.dataGb(), values.qosText(), values.voiceText(), values.smsText(), values.network(),
                    values.generation(), monthly, charge, req.validDays(), values.discountMonths(),
                    values.priceAfterDiscount(), tags.toArray(String[]::new), supplemented.toArray(String[]::new),
                    changed.toArray(String[]::new), base.collectedOn(), null, CurrentAdmin.get().loginId(), null,
                    base.sourceHash()));
        }
        audit.action(PROGRAM, "EDIT", "plan " + id, String.join(",", changed));
        return detail(id);
    }

    @Transactional
    public Detail updateActivationUrl(long id, String url) {
        find(id);
        String u = Texts.requireHttpsUrl(url, "개통 URL");
        planMapper.updateActivationUrl(id, u);
        audit.action(PROGRAM, "ACTIVATION_URL", "plan " + id, u);
        return detail(id);
    }

    /** 게시 예약 (여러 건을 같은 시각으로). 필수 항목이 빈 건은 건너뛴다. */
    @Transactional
    public ReviewService.BulkResult schedule(List<Long> ids, OffsetDateTime publishAt) {
        checkPublishAt(publishAt);
        List<Long> ok = new ArrayList<>();
        List<ReviewService.Skipped> skipped = new ArrayList<>();
        for (PlanAdminRow plan : planMapper.findPlansByIds(ids)) {
            String problem = publishable(plan);
            if (problem != null) {
                skipped.add(new ReviewService.Skipped(plan.id(), problem));
                continue;
            }
            planMapper.scheduleVersion(plan.draftVersionId(), publishAt);
            if (plan.publishedVersionId() == null && plan.status().equals("PENDING")) {
                planMapper.setStatus(plan.id(), "SCHEDULED");
            }
            ok.add(plan.id());
        }
        if (!ok.isEmpty()) {
            audit.action(PROGRAM, "SCHEDULE", "plan " + ok, publishAt.toString());
        }
        return new ReviewService.BulkResult(ok, skipped);
    }

    @Transactional
    public ReviewService.BulkResult publishNow(List<Long> ids) {
        List<Long> ok = new ArrayList<>();
        List<ReviewService.Skipped> skipped = new ArrayList<>();
        String by = CurrentAdmin.get().loginId();
        for (PlanAdminRow plan : planMapper.findPlansByIds(ids)) {
            String problem = publishable(plan);
            if (problem != null) {
                skipped.add(new ReviewService.Skipped(plan.id(), problem));
                continue;
            }
            planMapper.markVersionPublished(plan.draftVersionId(), by);
            planMapper.pointToVersion(plan.id(), plan.draftVersionId());
            ok.add(plan.id());
        }
        if (!ok.isEmpty()) {
            audit.action(PROGRAM, "PUBLISH", "plan " + ok, null);
        }
        return new ReviewService.BulkResult(ok, skipped);
    }

    @Transactional
    public ReviewService.BulkResult cancelSchedule(List<Long> ids) {
        List<Long> ok = new ArrayList<>();
        List<ReviewService.Skipped> skipped = new ArrayList<>();
        for (PlanAdminRow plan : planMapper.findPlansByIds(ids)) {
            if (plan.draftVersionId() == null || plan.publishAt() == null) {
                skipped.add(new ReviewService.Skipped(plan.id(), "게시 예약이 없습니다"));
                continue;
            }
            planMapper.cancelSchedule(plan.draftVersionId());
            if (plan.status().equals("SCHEDULED")) {
                planMapper.setStatus(plan.id(), "PENDING");
            }
            ok.add(plan.id());
        }
        if (!ok.isEmpty()) {
            audit.action(PROGRAM, "CANCEL_SCHEDULE", "plan " + ok, null);
        }
        return new ReviewService.BulkResult(ok, skipped);
    }

    @Transactional
    public Detail hide(long id, boolean hidden) {
        PlanAdminRow plan = find(id);
        if (plan.status().equals("ENDED")) {
            throw ApiException.conflict("판매 종료된 요금제입니다");
        }
        if (hidden) {
            planMapper.setStatus(id, "HIDDEN");
        } else if (plan.status().equals("HIDDEN")) {
            planMapper.restoreStatus(id);
        }
        audit.action(PROGRAM, hidden ? "HIDE" : "UNHIDE", "plan " + id, null);
        return detail(id);
    }

    /** 게시 후 7일 안에 직전 게시 버전으로 되돌린다 (결정 #27). */
    @Transactional
    public Detail rollback(long id) {
        PlanAdminRow plan = find(id);
        PlanVersionRow target = rollbackTarget(plan);
        if (target == null) {
            throw ApiException.conflict("되돌릴 수 있는 이전 게시 버전이 없거나 게시 후 "
                    + props.rollbackWindow().toDays() + "일이 지났습니다");
        }
        long current = plan.publishedVersionId();
        planMapper.discardVersion(current);
        planMapper.pointToVersion(id, target.id());
        audit.action(PROGRAM, "ROLLBACK", "plan " + id, "v" + current + " -> v" + target.id());
        return detail(id);
    }

    /** 게시 일정 달력: 최대 62일 */
    public List<CalendarRow> calendar(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.plusDays(62).isBefore(to)) {
            throw ApiException.badRequest("기간은 62일 이내로 지정해 주세요");
        }
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return planMapper.findCalendar(from.atStartOfDay(kst).toOffsetDateTime(), to.plusDays(1).atStartOfDay(kst).toOffsetDateTime());
    }

    /** 승인과 함께 예약할 때: 필수 항목이 비면 예약하지 않고 사유를 돌려준다 (승인은 그대로 진행). */
    public String trySchedule(long planId, long versionId, OffsetDateTime publishAt) {
        PlanAdminRow plan = planMapper.findPlan(planId);
        List<String> missing = PublishRules.missing(plan.planType(), planMapper.findVersion(versionId), plan.activationUrl());
        if (!missing.isEmpty()) {
            return "게시 예약 안 됨 — 비어 있는 항목: " + String.join(", ", missing);
        }
        planMapper.scheduleVersion(versionId, publishAt);
        if (plan.publishedVersionId() == null && plan.status().equals("PENDING")) {
            planMapper.setStatus(planId, "SCHEDULED");
        }
        return null;
    }

    /** 예약 시각: 지금 이후, 10분 단위 (PR-01 게시 예약 규칙), 1년 이내. */
    public void checkPublishAt(OffsetDateTime publishAt) {
        if (publishAt == null) {
            throw ApiException.badRequest("게시 일시를 지정해 주세요");
        }
        if (!publishAt.isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("게시 일시는 지금 이후여야 합니다");
        }
        if (publishAt.getMinute() % 10 != 0 || publishAt.getSecond() != 0 || publishAt.getNano() != 0) {
            throw ApiException.badRequest("게시 일시는 10분 단위로 지정해 주세요");
        }
        if (publishAt.isAfter(OffsetDateTime.now().plusYears(1))) {
            throw ApiException.badRequest("게시 일시는 1년 이내로 지정해 주세요");
        }
    }

    public static ItemValues values(PlanVersionRow v) {
        return new ItemValues(v.name(), v.dataText(), v.dataGb(), v.qosText(), v.voiceText(), v.smsText(), v.network(),
                v.generation(), v.monthlyPrice() != null ? v.monthlyPrice() : v.chargePrice(), v.discountMonths(),
                v.priceAfterDiscount());
    }

    static String state(PlanAdminRow p) {
        if (p.status().equals("ENDED") || p.status().equals("HIDDEN")) {
            return p.status();
        }
        if (p.draftVersionId() != null) {
            return p.publishAt() != null ? "SCHEDULED" : "DRAFT";
        }
        return p.publishedVersionId() != null ? "PUBLISHED" : "DRAFT";
    }

    private ListItem listItem(PlanAdminRow p) {
        List<String> missing = p.draftVersionId() == null ? List.of()
                : PublishRules.missing(p.planType(), planMapper.findVersion(p.draftVersionId()), p.activationUrl());
        return new ListItem(p, state(p), missing);
    }

    /** 예약 · 게시할 수 있으면 null, 아니면 이유 */
    private String publishable(PlanAdminRow plan) {
        if (plan.status().equals("ENDED")) {
            return "판매 종료된 요금제입니다";
        }
        if (plan.draftVersionId() == null) {
            return "게시 대기 버전이 없습니다";
        }
        List<String> missing = PublishRules.missing(plan.planType(), planMapper.findVersion(plan.draftVersionId()),
                plan.activationUrl());
        return missing.isEmpty() ? null : "비어 있는 항목: " + String.join(", ", missing);
    }

    private PlanVersionRow rollbackTarget(PlanAdminRow plan) {
        if (plan.publishedVersionId() == null || plan.publishedAt() == null
                || plan.publishedAt().isBefore(OffsetDateTime.now().minus(props.rollbackWindow()))) {
            return null;
        }
        return planMapper.findPreviousPublished(plan.id(), plan.publishedVersionId());
    }

    private static List<String> tags(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> tags = raw.stream().map(Texts::trim).filter(Objects::nonNull).distinct().toList();
        if (tags.size() > MAX_TAGS) {
            throw ApiException.badRequest("혜택 태그는 " + MAX_TAGS + "개까지입니다");
        }
        if (tags.stream().anyMatch(t -> t.length() > 20 || t.contains("|"))) {
            throw ApiException.badRequest("혜택 태그는 20자 이하로 입력해 주세요");
        }
        return tags;
    }

    private PlanAdminRow find(long id) {
        PlanAdminRow plan = planMapper.findPlan(id);
        if (plan == null) {
            throw ApiException.notFound("요금제");
        }
        return plan;
    }
}
