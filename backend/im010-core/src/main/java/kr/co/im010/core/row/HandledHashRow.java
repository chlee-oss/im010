package kr.co.im010.core.row;

/** 이미 승인되었거나 제외된 수집값. 같은 값이 다시 들어오면 새 점검 건을 만들지 않거나 "제외된 값과 동일"로 표시한다. */
public record HandledHashRow(
        String itemKey,
        String valueHash,
        String status
) {
}
