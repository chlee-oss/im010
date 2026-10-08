package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 메신저 채널 (웹훅). alertTypes 는 '|' 로 이은 문자열. */
public record ChannelRow(
        long id,
        String name,
        String kind,
        String webhookUrl,
        String alertTypes,
        boolean enabled,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
