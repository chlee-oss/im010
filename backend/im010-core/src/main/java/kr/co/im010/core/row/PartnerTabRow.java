package kr.co.im010.core.row;

/** 제휴사 사이트에서 발견한 미등록 탭. status = NEW(미확인) | IGNORED(수집 안 함으로 확인). */
public record PartnerTabRow(
        long id,
        String partnerCode,
        String url,
        String label,
        String status
) {
}
