package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/**
 * 메신저 채널. alertTypes 는 '|' 로 이은 문자열.
 * KAKAOWORK 는 webhookUrl = 봇 App Key, recipients = 멤버 이메일(줄바꿈), conversationId = 봇이 만든 채팅방 ID.
 */
public record ChannelRow(
        long id,
        String name,
        String kind,
        String webhookUrl,
        String alertTypes,
        boolean enabled,
        String updatedBy,
        OffsetDateTime updatedAt,
        String recipients,
        String conversationId
) {
}
