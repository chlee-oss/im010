package kr.co.im010.batch.notify;

import kr.co.im010.core.notify.AlertType;

/**
 * 운영자 알림 (백오피스 7장). 알림함(notification)에 넣으면 NotificationDispatcher 가 메신저 채널 · 메일로 보낸다.
 */
public interface Notifier {

    enum Level { INFO, WARN, URGENT }

    void send(AlertType type, Level level, String title, String body);

    /** dedupeKey 가 같은 알림은 24시간에 한 번만 */
    void sendOnce(AlertType type, Level level, String title, String body, String dedupeKey);
}
