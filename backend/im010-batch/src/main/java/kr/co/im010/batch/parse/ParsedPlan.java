package kr.co.im010.batch.parse;

import java.math.BigDecimal;

/**
 * 제휴사 요금제 페이지에서 추출한 요금제 한 건. 추출하지 못한 항목은 null.
 *
 * @param partnerPlanCode 제휴사 요금제 코드 (상세 링크의 no)
 * @param network         SKT | KT | LGU
 * @param dataGb          기본 제공량, null 이면 무제한이거나 추출 실패 (dataText 로 구분)
 * @param price           후불은 월 요금, 선불은 충전 금액
 * @param detailUrl       제휴사 요금제 상세 페이지 (개통 URL 기본값)
 * @param siteOrder       제휴사 페이지의 순서 (1부터)
 */
public record ParsedPlan(
        String partnerPlanCode,
        String name,
        String network,
        String generation,
        String dataText,
        BigDecimal dataGb,
        String qosText,
        String voiceText,
        String smsText,
        Integer price,
        Integer discountMonths,
        Integer priceAfterDiscount,
        String detailUrl,
        String sourceUrl,
        int siteOrder
) {

    /** 같은 요금제 판단 키: 제휴사 요금제 코드, 없으면 요금제명|망 (BA-02). */
    public String key() {
        return partnerPlanCode != null ? partnerPlanCode : name + "|" + network;
    }

    public boolean missingRequired() {
        return name == null || name.isBlank() || price == null;
    }
}
