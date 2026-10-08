package kr.co.im010.admin.review;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.row.ItemValues;
import kr.co.im010.core.row.ReviewItemRow;

/** 배치 수집 건 (BA-02 · BA-03 화면용). */
public record ItemDto(
        long id,
        String partnerCode,
        String partnerName,
        String urlType,
        String changeType,
        String status,
        Long planId,
        String partnerPlanCode,
        ItemValues values,
        String detailUrl,
        String sourceUrl,
        Integer siteOrder,
        List<String> changedFields,
        List<String> warnings,
        List<String> editedFields,
        String reviewer,
        OffsetDateTime reviewedAt,
        String approver,
        OffsetDateTime approvedAt,
        String reason,
        String memo,
        LocalDate collectedOn
) {

    public static ItemDto of(ReviewItemRow r) {
        return new ItemDto(r.id(), r.partnerCode(), r.partnerName(), r.urlType(), r.changeType(), r.status(),
                r.planId(), r.partnerPlanCode(), values(r), r.detailUrl(), r.sourceUrl(), r.siteOrder(),
                Texts.split(r.changedFields()), Texts.split(r.warnings()), Texts.split(r.editedFields()),
                r.reviewer(), r.reviewedAt(), r.approver(), r.approvedAt(), r.reason(), r.memo(), r.collectedOn());
    }

    static ItemValues values(ReviewItemRow r) {
        return new ItemValues(r.name(), r.dataText(), r.dataGb(), r.qosText(), r.voiceText(), r.smsText(),
                r.network(), r.generation(), r.price(), r.discountMonths(), r.priceAfterDiscount());
    }
}
