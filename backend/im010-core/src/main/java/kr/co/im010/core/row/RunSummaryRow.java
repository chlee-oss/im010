package kr.co.im010.core.row;

/** 하루 수집 결과 요약 한 줄 (제휴사 · 유형별 마지막 실행). */
public record RunSummaryRow(
        String partnerName,
        String urlType,
        String result,
        int collectedCount,
        int newCount,
        int changedCount,
        int endedCount
) {
}
