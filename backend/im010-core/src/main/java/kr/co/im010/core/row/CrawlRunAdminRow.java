package kr.co.im010.core.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 실행 이력 (BA-01 [실행 이력]). */
public record CrawlRunAdminRow(
        long id,
        long jobId,
        String trigger,
        int attempt,
        String partnerCode,
        String partnerName,
        String urlType,
        LocalDate runOn,
        String result,
        int urlCount,
        int collectedCount,
        int newCount,
        int changedCount,
        int endedCount,
        String message,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
