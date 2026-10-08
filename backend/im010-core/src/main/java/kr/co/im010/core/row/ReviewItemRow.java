package kr.co.im010.core.row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 배치 수집 건 (BA-02 · BA-03 화면). 배열 칼럼은 '|' 로 이은 문자열. */
public record ReviewItemRow(
        long id,
        long runId,
        String partnerCode,
        String partnerName,
        String urlType,
        String itemKey,
        String partnerPlanCode,
        Long planId,
        String changeType,
        String status,
        String name,
        String dataText,
        BigDecimal dataGb,
        String qosText,
        String voiceText,
        String smsText,
        String network,
        String generation,
        Integer price,
        Integer discountMonths,
        Integer priceAfterDiscount,
        String detailUrl,
        String sourceUrl,
        Integer siteOrder,
        String changedFields,
        String warnings,
        String editedFields,
        String valueHash,
        String reviewer,
        String reason,
        String memo,
        LocalDate collectedOn,
        OffsetDateTime reviewedAt,
        String approver,
        OffsetDateTime approvedAt
) {
}
