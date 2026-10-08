package kr.co.im010.core.row;

/** 제휴사 요금제 코드로 찾은 요금제 (이달의 요금제 연결용). */
public record PlanCodeRow(
        long planId,
        String partnerPlanCode,
        String status
) {
}
