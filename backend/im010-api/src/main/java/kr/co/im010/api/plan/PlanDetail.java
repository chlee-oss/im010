package kr.co.im010.api.plan;

import java.time.LocalDate;
import java.util.List;

/**
 * S2 요금제 상세 응답.
 *
 * @param ended        판매 종료 여부 (true 면 개통하기 비활성 + 안내 배너)
 * @param basisDate    "○○ 기준" 날짜 (게시된 수집일)
 * @param activatePath 개통하기 이동 주소 (프런트는 제휴사 URL을 직접 걸지 않는다)
 * @param similar      비슷한 요금제 (후불만, 선불형 상세는 빈 목록)
 */
public record PlanDetail(
        PlanSummary plan,
        String voice,
        String sms,
        Integer discountMonths,
        Integer priceAfterDiscount,
        boolean ended,
        LocalDate basisDate,
        String activatePath,
        List<PlanSummary> similar
) {
}
