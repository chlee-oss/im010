package kr.co.im010.api.plan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import kr.co.im010.api.web.NotFoundException;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.PlanRow;

@Service
public class PlanService {

    static final String POSTPAID = "POSTPAID";
    static final String PREPAID = "PREPAID";
    static final int MONTHLY_PICK_LIMIT = 7;
    static final int SIMILAR_LIMIT = 3;

    private final PlanMapper planMapper;

    public PlanService(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    public List<PlanSummary> findPublished(String planType, String partnerCode) {
        String type = normalizeType(planType);
        return planMapper.findPublished(type, blankToNull(partnerCode)).stream().map(PlanSummary::from).toList();
    }

    /**
     * 계산기 — 게시 중인 후불 요금제만 대상 (선불은 비교 제외).
     *
     * @param dataGb null 이면 무제한 요금제만
     */
    public Optional<PlanSummary> findCheapest(Network network, BigDecimal dataGb) {
        if (dataGb != null && dataGb.signum() <= 0) {
            throw new IllegalArgumentException("dataGb must be positive");
        }
        return Optional.ofNullable(planMapper.findCheapestPostpaid(network.name(), dataGb)).map(PlanSummary::from);
    }

    public PlanDetail findDetail(long id) {
        PlanRow row = planMapper.findDetail(id);
        if (row == null) {
            throw new NotFoundException("plan " + id);
        }
        boolean ended = "ENDED".equals(row.status());
        List<PlanSummary> similar = POSTPAID.equals(row.planType())
                ? planMapper.findSimilarPostpaid(id, row.network(), row.dataGb(), SIMILAR_LIMIT).stream().map(PlanSummary::from).toList()
                : List.of();
        return new PlanDetail(
                PlanSummary.from(row), row.voiceText(), row.smsText(),
                row.discountMonths(), row.priceAfterDiscount(),
                ended, row.collectedOn(),
                "/go/" + id + "?from=S2",
                similar);
    }

    public List<PlanSummary> findMonthlyPicks() {
        return planMapper.findMonthlyPicks(MONTHLY_PICK_LIMIT).stream().map(PlanSummary::from).toList();
    }

    /** 신뢰 지표 "비교 가능한 요금제" = 게시 중인 후불 요금제 수. */
    public int countComparable() {
        return planMapper.countPublished(POSTPAID);
    }

    static String normalizeType(String planType) {
        if (planType == null || planType.isBlank()) {
            return POSTPAID;
        }
        String t = planType.trim().toUpperCase();
        if (!t.equals(POSTPAID) && !t.equals(PREPAID)) {
            throw new IllegalArgumentException("type must be POSTPAID or PREPAID");
        }
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
