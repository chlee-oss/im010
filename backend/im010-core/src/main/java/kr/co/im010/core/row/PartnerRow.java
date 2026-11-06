package kr.co.im010.core.row;

public record PartnerRow(
        String code,
        String name,
        String chipBg,
        String chipFg,
        String homepageUrl,
        /** 로고 이미지 주소, 없으면 null — 화면은 브랜드 칩을 쓴다 */
        String logoUrl
) {
}
