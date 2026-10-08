package kr.co.im010.core.row;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 제휴사관리(PA-01) · 스케줄관리(BA-01) 목록의 제휴사 한 건. */
public record PartnerAdminRow(
        String code,
        String name,
        String chipBg,
        String chipFg,
        String homepageUrl,
        boolean exposed,
        int sortOrder,
        int postpaidUrls,
        int prepaidUrls,
        int monthlyUrls,
        int newTabs,
        Boolean scheduleEnabled,
        String scheduleDays,
        LocalTime scheduleTime,
        OffsetDateTime lastRunAt,
        String lastRunResult,
        boolean jobWaiting
) {
}
