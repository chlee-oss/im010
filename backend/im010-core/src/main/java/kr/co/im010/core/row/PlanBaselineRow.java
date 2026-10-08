package kr.co.im010.core.row;

import java.math.BigDecimal;

/**
 * 변경 감지 기준값: 요금제의 최신 버전 (승인된 값). 버전이 없으면 값 필드는 null.
 * price 는 후불 월 요금 또는 선불 충전 금액, supplementedFields 는 운영자 보완 항목을 '|' 로 이은 문자열,
 * sourceHash 는 그 버전을 만든 수집값의 지문.
 */
public record PlanBaselineRow(
        long planId,
        String partnerPlanCode,
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
        String supplementedFields,
        String sourceHash
) {
}
