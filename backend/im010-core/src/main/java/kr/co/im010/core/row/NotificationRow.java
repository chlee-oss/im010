package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 알림함의 알림 한 건. deliveries = 발송 대상별 결과 요약 ("이름:상태" 를 '|' 로 이은 문자열, 목록 화면용) */
public record NotificationRow(
        long id,
        String alertType,
        String level,
        String title,
        String body,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt,
        String deliveries
) {
}
