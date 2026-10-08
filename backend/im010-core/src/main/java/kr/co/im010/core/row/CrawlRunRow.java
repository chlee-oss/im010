package kr.co.im010.core.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 유형별 수집 실행 결과 (BA-01 [실행 이력]). result = SUCCESS | FAILED | ABNORMAL. */
public record CrawlRunRow(
        long jobId,
        String partnerCode,
        String urlType,
        LocalDate runOn,
        String result,
        int urlCount,
        int collectedCount,
        int newCount,
        int changedCount,
        int endedCount,
        String message,
        OffsetDateTime startedAt
) {
}
