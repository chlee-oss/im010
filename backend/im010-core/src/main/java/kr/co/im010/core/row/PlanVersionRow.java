package kr.co.im010.core.row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 요금제 버전 전체 값 (PR-01 상세 · 승인 · 게시). 배열 칼럼은 '|' 로 이은 문자열. */
public record PlanVersionRow(
        long id,
        long planId,
        int versionNo,
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
        String tags,
        String supplementedFields,
        String changedFields,
        LocalDate collectedOn,
        OffsetDateTime publishAt,
        OffsetDateTime publishedAt,
        String publishedBy,
        String reviewer,
        String approver,
        OffsetDateTime approvedAt,
        OffsetDateTime discardedAt,
        Long crawlItemId,
        String sourceHash
) {
}
