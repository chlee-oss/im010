package kr.co.im010.core.row;

import java.time.LocalDate;

/** 수집 작업 한 건. urlTypes 는 '|' 로 이은 문자열, null 이면 등록된 전체 유형. */
public record CrawlJobRow(
        long id,
        String partnerCode,
        String trigger,
        String urlTypes,
        int attempt,
        LocalDate runOn
) {
}
