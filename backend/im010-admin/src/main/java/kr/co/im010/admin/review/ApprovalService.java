package kr.co.im010.admin.review;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.plan.Fields;
import kr.co.im010.admin.plan.PlanAdminService;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.mapper.ReviewMapper;
import kr.co.im010.core.row.ItemValues;
import kr.co.im010.core.row.NewPlanVersion;
import kr.co.im010.core.row.PlanAdminRow;
import kr.co.im010.core.row.PlanVersionRow;
import kr.co.im010.core.row.ReviewItemRow;

/**
 * 승인관리 (BA-03): 승인 = 요금제관리(PR-01)로 이관. 요금제 · 버전을 만들고 "게시 대기"로 둔다 (외부 노출은 게시 예약으로).
 * <pre>
 * 신규 · 변경      새 버전 생성 (예전 미게시 버전은 대체) — 수집되지 않은 항목은 직전 버전의 보완값을 이어받는다
 * 판매 재개        판매 종료를 되돌린다
 * 이달의 요금제    게시 중 요금제와 연결해 이달의 요금제에 노출
 * </pre>
 * 반려하면 점검 대기로 돌아간다 (사유 필수).
 */
@Service
public class ApprovalService {

    static final String PROGRAM = "BA-03";

    public record Approved(long id, Long planId, Long versionId, boolean replacedSchedule, String note) {
    }

    public record Result(List<Approved> approved, List<ReviewService.Skipped> skipped) {
    }

    private final ReviewMapper reviewMapper;
    private final PlanAdminMapper planMapper;
    private final PlanAdminService planService;
    private final AuditService audit;

    public ApprovalService(ReviewMapper reviewMapper, PlanAdminMapper planMapper, PlanAdminService planService,
                           AuditService audit) {
        this.reviewMapper = reviewMapper;
        this.planMapper = planMapper;
        this.planService = planService;
        this.audit = audit;
    }

    /** @param publishAt 승인과 함께 게시 예약 (선택, 요금제관리 승인 권한 필요) */
    @Transactional
    public Result approve(List<Long> ids, OffsetDateTime publishAt) {
        if (publishAt != null) {
            CurrentAdmin.require(PlanAdminService.PROGRAM, Action.APPROVE);
            planService.checkPublishAt(publishAt);
        }
        String approver = CurrentAdmin.get().loginId();
        List<Approved> approved = new ArrayList<>();
        List<ReviewService.Skipped> skipped = new ArrayList<>();
        for (ReviewItemRow row : reviewMapper.findItemsByIds(ids)) {
            if (!row.status().equals("APPROVAL_REQUESTED")) {
                skipped.add(new ReviewService.Skipped(row.id(), "승인 요청 상태가 아닙니다"));
                continue;
            }
            try {
                Approved a = switch (row.urlType()) {
                    case "MONTHLY" -> approveMonthly(row);
                    default -> row.changeType().equals("ENDED") ? approveResume(row) : approvePlan(row, approver, publishAt);
                };
                reviewMapper.markApproved(row.id(), approver);
                approved.add(a);
            } catch (ApiException e) {
                skipped.add(new ReviewService.Skipped(row.id(), e.getMessage()));
            }
        }
        if (!approved.isEmpty()) {
            audit.action(PROGRAM, "APPROVE", "crawl_item " + approved.stream().map(Approved::id).toList(),
                    publishAt != null ? "publishAt " + publishAt : null);
        }
        return new Result(approved, skipped);
    }

    @Transactional
    public ReviewService.BulkResult reject(List<Long> ids, String reason) {
        String r = Texts.trim(reason);
        if (r == null) {
            throw ApiException.badRequest("반려 사유를 입력해 주세요");
        }
        List<Long> ok = new ArrayList<>();
        List<ReviewService.Skipped> skipped = new ArrayList<>();
        for (ReviewItemRow row : reviewMapper.findItemsByIds(ids)) {
            if (row.status().equals("APPROVAL_REQUESTED")) {
                ok.add(row.id());
            } else {
                skipped.add(new ReviewService.Skipped(row.id(), "승인 요청 상태가 아닙니다"));
            }
        }
        if (!ok.isEmpty()) {
            reviewMapper.markRejected(ok, CurrentAdmin.get().loginId(), r);
            audit.action(PROGRAM, "REJECT", "crawl_item " + ok, r);
        }
        return new ReviewService.BulkResult(ok, skipped);
    }

    private Approved approvePlan(ReviewItemRow row, String approver, OffsetDateTime publishAt) {
        ItemValues values = ItemDto.values(row);
        List<String> missing = Fields.missingForVersion(values);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("비어 있는 항목: " + String.join(", ", missing));
        }
        String type = row.urlType();
        String activationUrl = row.detailUrl() != null ? row.detailUrl() : row.sourceUrl();
        Long planId = row.planId();
        if (planId == null && row.partnerPlanCode() != null) {
            planId = planMapper.findPlanIdByCode(row.partnerCode(), type, row.partnerPlanCode());
        }
        if (planId == null) {
            planId = planMapper.insertPlan(row.partnerCode(), type, row.partnerPlanCode(), activationUrl);
        }
        PlanAdminRow plan = planMapper.findPlan(planId);
        if (row.partnerPlanCode() != null && !row.partnerPlanCode().equals(plan.partnerPlanCode())) {
            // [기존 요금제와 연결]: 제휴사 요금제 코드가 바뀐 경우 다음 수집부터 새 코드로 맞춘다
            Long other = planMapper.findPlanIdByCode(row.partnerCode(), type, row.partnerPlanCode());
            if (other != null && !other.equals(planId)) {
                throw ApiException.conflict("제휴사 요금제 코드가 요금제 #" + other + "와 겹칩니다");
            }
            planMapper.updatePartnerPlanCode(planId, row.partnerPlanCode());
        }
        PlanVersionRow prev = planMapper.findLatestVersion(planId);
        boolean replaced = planMapper.countScheduledDrafts(planId) > 0;
        planMapper.discardDrafts(planId);

        // 수집되지 않은 항목은 직전 버전에서 운영자가 보완한 값을 이어받는다 (결정 #21)
        Set<String> carried = new LinkedHashSet<>();
        Set<String> prevSupplemented = prev == null ? Set.of() : Set.copyOf(Texts.split(prev.supplementedFields()));
        ItemValues merged = new ItemValues(
                pick(values.name(), prev == null ? null : prev.name(), "name", prevSupplemented, carried),
                pick(values.dataText(), prev == null ? null : prev.dataText(), "dataText", prevSupplemented, carried),
                pick(values.dataGb(), prev == null ? null : prev.dataGb(), "dataGb", prevSupplemented, carried),
                pick(values.qosText(), prev == null ? null : prev.qosText(), "qos", prevSupplemented, carried),
                pick(values.voiceText(), prev == null ? null : prev.voiceText(), "voice", prevSupplemented, carried),
                pick(values.smsText(), prev == null ? null : prev.smsText(), "sms", prevSupplemented, carried),
                values.network(), values.generation(), values.price(),
                pick(values.discountMonths(), prev == null ? null : prev.discountMonths(), "discountMonths", prevSupplemented, carried),
                pick(values.priceAfterDiscount(), prev == null ? null : prev.priceAfterDiscount(), "priceAfterDiscount", prevSupplemented, carried));
        // 수집하지 않는 항목 (선불 사용 기간 · 혜택 태그)은 그대로 이어받는다
        Integer validDays = prev == null ? null : prev.validDays();
        if (validDays != null && prevSupplemented.contains("validDays")) {
            carried.add("validDays");
        }
        String[] tags = prev == null ? new String[0] : Texts.split(prev.tags()).toArray(String[]::new);
        List<String> changed = prev == null ? List.of() : Fields.diff(PlanAdminService.values(prev), merged);
        boolean prepaid = type.equals("PREPAID");

        long versionId = planMapper.insertVersion(new NewPlanVersion(planId, planMapper.nextVersionNo(planId),
                merged.name(), merged.dataText(), merged.dataGb(), merged.qosText(), merged.voiceText(), merged.smsText(),
                merged.network(), merged.generation(), prepaid ? null : merged.price(), prepaid ? merged.price() : null,
                validDays, merged.discountMonths(), merged.priceAfterDiscount(), tags, carried.toArray(String[]::new),
                changed.toArray(String[]::new), row.collectedOn(), row.reviewer(), approver, row.id(), row.valueHash()));
        planMapper.setActivationUrlIfEmpty(planId, activationUrl);
        if (plan.status().equals("ENDED")) {
            planMapper.restoreStatus(planId);
        } else if (plan.status().equals("SCHEDULED")) {
            planMapper.setStatus(planId, "PENDING");   // 예약돼 있던 미게시 버전이 대체됨
        }

        String note = null;
        if (publishAt != null) {
            note = planService.trySchedule(planId, versionId, publishAt);
        }
        return new Approved(row.id(), planId, versionId, replaced, note);
    }

    private Approved approveResume(ReviewItemRow row) {
        if (row.planId() == null) {
            throw ApiException.badRequest("판매 재개할 요금제가 없습니다");
        }
        planMapper.restoreStatus(row.planId());
        return new Approved(row.id(), row.planId(), null, false, "판매 재개");
    }

    private Approved approveMonthly(ReviewItemRow row) {
        Long planId = row.planId();
        if (planId == null && row.partnerPlanCode() != null) {
            planId = planMapper.findLivePlanIdByCode(row.partnerCode(), row.partnerPlanCode());
        }
        if (planId == null) {
            throw ApiException.badRequest("연결할 요금제가 아직 없습니다. 해당 요금제를 먼저 승인해 주세요");
        }
        String note = null;
        if (planMapper.existsExposedPick(planId)) {
            note = "이미 이달의 요금제에 있습니다";
        } else {
            planMapper.insertMonthlyPick(row.partnerCode(), planId, row.siteOrder() == null ? 0 : row.siteOrder(),
                    row.partnerPlanCode(), row.id(), row.collectedOn());
        }
        return new Approved(row.id(), planId, null, false, note);
    }

    private static <T> T pick(T collected, T previous, String field, Set<String> prevSupplemented, Set<String> carried) {
        if (collected == null && previous != null && prevSupplemented.contains(field)) {
            carried.add(field);
            return previous;
        }
        return collected;
    }
}
