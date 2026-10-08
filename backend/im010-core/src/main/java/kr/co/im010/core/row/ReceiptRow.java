package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/**
 * 접수 · 이동 기록 한 건 (RC-01 알뜰폰접수신청 · RC-02 인터넷접수신청). 개인정보 없음.
 * RC-01: partnerName = 제휴사, targetName = 요금제명 (접수 당시 버전), category = 후불 | 선불
 * RC-02: partnerName = 제휴업체, targetName = 상품명, category = 단독 | 결합, carrier = 통신사
 */
public record ReceiptRow(
        long id,
        OffsetDateTime createdAt,
        String kind,
        long targetId,
        String partnerRef,
        String partnerName,
        String targetName,
        Integer versionNo,
        String category,
        String carrier,
        String targetUrl,
        String fromPage,
        String result
) {
}
