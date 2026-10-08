package kr.co.im010.core.row;

/** 수집 URL (PA-01). urlType = POSTPAID | PREPAID | MONTHLY, 같은 유형에 탭별로 여러 개. */
public record CollectUrlRow(
        long id,
        String partnerCode,
        String urlType,
        String url,
        String label,
        int sortOrder
) {
}
