package kr.co.im010.core.row;

/** 노출 중인 이달의 요금제 항목. */
public record MonthlyPickRow(
        long id,
        String partnerPlanCode,
        Long planId,
        int siteOrder
) {
}
