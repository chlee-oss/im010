package kr.co.im010.core.row;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 배치 수집 건 (BA-02) 저장용. price 는 후불이면 월 요금, 선불이면 충전 금액. */
public record CrawlItemRow(
        long runId,
        String partnerCode,
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
        String[] changedFields,
        String[] warnings,
        String valueHash,
        LocalDate collectedOn
) {
}
