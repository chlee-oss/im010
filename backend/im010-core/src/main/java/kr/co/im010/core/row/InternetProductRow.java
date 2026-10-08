package kr.co.im010.core.row;

/** benefits 는 DB 배열을 '|' 로 이어 붙인 문자열. */
public record InternetProductRow(
        long id,
        String carrier,
        String productType,
        String name,
        int monthlyPrice,
        String benefits
) {
}
