package kr.co.im010.core.row;

/** 인터넷 상품 (PR-02). benefits 는 '|' 로 이은 문자열. */
public record InternetProductAdminRow(
        long id,
        String carrier,
        String productType,
        String name,
        int monthlyPrice,
        String benefits,
        Long internetPartnerId,
        String partnerName,
        String partnerStatus,
        String applyUrl,
        String partnerApplyUrl,
        int sortOrder,
        boolean exposed
) {
}
