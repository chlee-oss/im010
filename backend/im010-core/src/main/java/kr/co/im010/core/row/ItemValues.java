package kr.co.im010.core.row;

import java.math.BigDecimal;

/** 운영자가 고칠 수 있는 수집 · 요금제 항목. price 는 후불 월 요금 또는 선불 충전 금액. */
public record ItemValues(
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
        Integer priceAfterDiscount
) {
}
