package kr.co.im010.core.row;

/** 보낼 차례가 된 발송 한 건 (채널 웹훅 또는 메일). channelKind · webhookUrl 이 null 이면 메일. */
public record DeliveryRow(
        long id,
        long notificationId,
        String target,
        int attempts,
        String channelKind,
        String webhookUrl,
        String alertType,
        String level,
        String title,
        String body
) {
}
