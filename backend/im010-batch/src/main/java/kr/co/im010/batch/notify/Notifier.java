package kr.co.im010.batch.notify;

/**
 * 운영자 알림 (백오피스 7장). 채널은 사내 메신저 + 메일 (결정 #25) — 연동 전까지는 로그로 남긴다.
 */
public interface Notifier {

    enum Level { INFO, WARN, URGENT }

    void send(Level level, String title, String body);
}
