package kr.co.im010.core.row;

/**
 * 인터넷 신청 포워딩 판단에 필요한 상품 정보.
 * applyUrl 은 상품별 URL, 없으면 계약 중인 제휴업체의 기본 URL (SQL에서 결정).
 */
public record InternetForwardTarget(
        long id,
        boolean exposed,
        Long internetPartnerId,
        String applyUrl
) {
}
