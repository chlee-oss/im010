package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 게시 일정 (PR-01 [게시 일정]): 예약 · 게시된 버전 한 건. */
public record CalendarRow(
        long versionId,
        long planId,
        int versionNo,
        String partnerName,
        String planType,
        String name,
        OffsetDateTime publishAt,
        OffsetDateTime publishedAt,
        String publishedBy
) {
}
