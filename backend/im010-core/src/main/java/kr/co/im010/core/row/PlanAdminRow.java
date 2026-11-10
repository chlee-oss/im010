package kr.co.im010.core.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 요금제관리(PR-01) 목록 · 상세의 요금제 한 건. 이름 · 요금 · 망 · 할인은 고객에게 보이는 값(게시된 버전),
 * 한 번도 게시되지 않은 요금제만 대기 버전 값. 게시하지 않은 새 값은 상세 화면(draft)에서 본다.
 *
 * @param draftVersionId 게시 전 버전 (게시 대기 · 예약), 없으면 null
 * @param mainPick       메인 "제휴사별 요금제" 노출 순서 (1 · 2), 없으면 null
 * @param discountMonths     할인 기간(개월, 후불). 이름 · 요금과 같이 고객에게 보이는 값
 * @param priceAfterDiscount 할인 기간이 끝난 뒤 월 요금
 */
public record PlanAdminRow(
        long id,
        String partnerCode,
        String partnerName,
        String planType,
        String partnerPlanCode,
        String status,
        String activationUrl,
        Long publishedVersionId,
        Long draftVersionId,
        String name,
        Integer price,
        String network,
        OffsetDateTime publishedAt,
        OffsetDateTime publishAt,
        LocalDate lastCollectedOn,
        LocalDate endedOn,
        Integer mainPick,
        Integer discountMonths,
        Integer priceAfterDiscount
) {
}
