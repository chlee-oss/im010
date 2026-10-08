package kr.co.im010.core.row;

import java.time.LocalDate;

/** 기준일 경과 확인: 제휴사별 게시 중 요금제의 최근 수집일 */
public record StaleDataRow(String partnerCode, String partnerName, LocalDate lastCollectedOn) {
}
