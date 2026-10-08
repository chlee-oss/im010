package kr.co.im010.core.row;

/** 관리자 메일 수신 설정. mailAlerts 는 '|' 로 이은 알림 종류. */
public record MailRecipientRow(long id, String loginId, String name, String email, String mailAlerts) {
}
