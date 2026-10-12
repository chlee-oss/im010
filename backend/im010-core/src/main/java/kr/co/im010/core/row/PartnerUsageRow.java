package kr.co.im010.core.row;

/** 제휴사 삭제 전 확인: 남겨야 할 이력 건수 · 수집 실행 중 여부. */
public record PartnerUsageRow(
        int plans,
        int monthlyPicks,
        int crawlRuns,
        int crawlItems,
        boolean running
) {

    /** 요금제 · 수집 이력이 하나라도 있으면 지우지 않고 삭제 표시만 한다. */
    public boolean hasHistory() {
        return plans + monthlyPicks + crawlRuns + crawlItems > 0;
    }
}
