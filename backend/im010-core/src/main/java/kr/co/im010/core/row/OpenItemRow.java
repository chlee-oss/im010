package kr.co.im010.core.row;

/** 점검 대기 · 승인 요청 중인 수집 건 (같은 요금제는 한 건만 열어 둔다). */
public record OpenItemRow(
        long id,
        String itemKey,
        String valueHash
) {
}
