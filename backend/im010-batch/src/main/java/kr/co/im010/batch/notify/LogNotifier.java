package kr.co.im010.batch.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 메신저 연동 전 기본 알림: 로그에 남긴다. */
@Component
public class LogNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger("im010.alert");

    @Override
    public void send(Level level, String title, String body) {
        if (level == Level.INFO) {
            log.info("[알림] {} — {}", title, body);
        } else {
            log.warn("[알림:{}] {} — {}", level, title, body);
        }
    }
}
