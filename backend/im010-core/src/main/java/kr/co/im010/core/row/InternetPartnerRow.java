package kr.co.im010.core.row;

import java.time.LocalDate;

/** 인터넷 제휴업체 (PA-01 [인터넷]). status = ACTIVE(계약 중) | ENDED(종료) */
public record InternetPartnerRow(
        long id,
        String name,
        String carrier,
        String applyUrl,
        String status,
        String businessNo,
        String contactName,
        String contactPhone,
        LocalDate contractStart,
        LocalDate contractEnd,
        String memo,
        int productCount
) {
}
