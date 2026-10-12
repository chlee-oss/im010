package kr.co.im010.batch.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.notify.AlertType;

/** 알림함에 넣는다 (발송은 NotificationDispatcher). 로그(im010.alert)에도 남긴다. */
@Component
public class OutboxNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger("im010.alert");

    private final NotifyMapper notifyMapper;

    public OutboxNotifier(NotifyMapper notifyMapper) {
        this.notifyMapper = notifyMapper;
    }

    @Override
    public void send(AlertType type, Level level, String title, String body) {
        sendOnce(type, level, title, body, null);
    }

    @Override
    public void sendOnce(AlertType type, Level level, String title, String body, String dedupeKey) {
        if (level == Level.INFO) {
            log.info("[알림:{}] {} — {}", type, title, body);
        } else {
            log.warn("[알림:{}:{}] {} — {}", type, level, title, body);
        }
        try {
            notifyMapper.insertNotification(type.name(), level.name(), cut(title, 200), body, dedupeKey);
        } catch (RuntimeException e) {
            log.error("알림함에 넣지 못했습니다: {}", title, e);   // 알림 실패가 수집 · 게시를 막지 않게
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
