package kr.co.im010.core.row;

import java.time.LocalDate;

/** 판매 종료 요금제 한 줄 (sitemap.xml — 종료 직후 일정 기간 남겨 검색엔진이 410 을 빨리 확인하게). */
public record PlanEndedRow(
        long id,
        LocalDate endedOn
) {
}
