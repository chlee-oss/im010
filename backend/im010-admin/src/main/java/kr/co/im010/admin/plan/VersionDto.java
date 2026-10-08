package kr.co.im010.admin.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.row.PlanVersionRow;

/** 요금제 버전 (화면용). state = PUBLISHED 게시 이력 있음 · SCHEDULED 게시 예약 · DRAFT 게시 대기 · DISCARDED 대체됨 */
public record VersionDto(
        long id,
        int versionNo,
        String state,
        String name,
        String dataText,
        BigDecimal dataGb,
        String qosText,
        String voiceText,
        String smsText,
        String network,
        String generation,
        Integer monthlyPrice,
        Integer chargePrice,
        Integer validDays,
        Integer discountMonths,
        Integer priceAfterDiscount,
        List<String> tags,
        List<String> supplementedFields,
        List<String> changedFields,
        LocalDate collectedOn,
        OffsetDateTime publishAt,
        OffsetDateTime publishedAt,
        String publishedBy,
        String reviewer,
        String approver,
        OffsetDateTime approvedAt,
        Long crawlItemId
) {

    public static VersionDto of(PlanVersionRow v) {
        if (v == null) {
            return null;
        }
        String state = v.discardedAt() != null ? "DISCARDED"
                : v.publishedAt() != null ? "PUBLISHED"
                : v.publishAt() != null ? "SCHEDULED" : "DRAFT";
        return new VersionDto(v.id(), v.versionNo(), state, v.name(), v.dataText(), v.dataGb(), v.qosText(),
                v.voiceText(), v.smsText(), v.network(), v.generation(), v.monthlyPrice(), v.chargePrice(),
                v.validDays(), v.discountMonths(), v.priceAfterDiscount(), Texts.split(v.tags()),
                Texts.split(v.supplementedFields()), Texts.split(v.changedFields()), v.collectedOn(), v.publishAt(),
                v.publishedAt(), v.publishedBy(), v.reviewer(), v.approver(), v.approvedAt(), v.crawlItemId());
    }
}
