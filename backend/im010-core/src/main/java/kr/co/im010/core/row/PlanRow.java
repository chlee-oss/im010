package kr.co.im010.core.row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 게시 중 버전 기준의 요금제 한 건. 목록 · 상세 · 계산기 · 이달의 요금제가 같은 행을 쓴다.
 * dataGb 가 null 이면 데이터 무제한. tags 는 DB 배열을 '|' 로 이어 붙인 문자열.
 */
public record PlanRow(
        long id,
        String partnerCode,
        String partnerName,
        String planType,
        String status,
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
        LocalDate collectedOn,
        OffsetDateTime publishedAt
) {
}
