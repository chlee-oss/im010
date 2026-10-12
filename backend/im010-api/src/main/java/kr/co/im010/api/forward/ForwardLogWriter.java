package kr.co.im010.api.forward;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import kr.co.im010.core.mapper.ForwardLogMapper;
import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.row.ForwardLog;

/**
 * 이동 기록은 고객을 기다리게 하지 않도록 비동기로 저장한다. 실패해도 이동은 막지 않는다.
 * 이동할 URL이 없으면(NO_URL) 운영자 알림을 알림함에 넣는다 — 같은 대상은 24시간에 한 번 (7장 "포워딩 URL 없음").
 */
@Component
public class ForwardLogWriter {

    private static final Logger log = LoggerFactory.getLogger(ForwardLogWriter.class);

    private final ForwardLogMapper forwardLogMapper;
    private final NotifyMapper notifyMapper;

    public ForwardLogWriter(ForwardLogMapper forwardLogMapper, NotifyMapper notifyMapper) {
        this.forwardLogMapper = forwardLogMapper;
        this.notifyMapper = notifyMapper;
    }

    @Async
    public void write(ForwardLog entry) {
        try {
            forwardLogMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("forward_log insert failed: kind={} target={} result={}", entry.kind(), entry.targetId(), entry.result(), e);
        }
        if ("NO_URL".equals(entry.result())) {
            boolean plan = "PLAN".equals(entry.kind());
            try {
                notifyMapper.insertNotification(AlertType.FORWARD_NO_URL.name(), "URGENT",
                        (plan ? "개통하기" : "인터넷 상담 신청") + " 이동 URL이 없습니다",
                        (plan ? "요금제 #" : "인터넷 상품 #") + entry.targetId() + " — 고객이 눌렀지만 이동하지 못했습니다. "
                                + (plan ? "요금제관리에서 개통 URL을" : "인터넷관리 · 제휴사관리 [인터넷]에서 신청 URL을") + " 등록해 주세요",
                        "NO_URL:" + entry.kind() + ":" + entry.targetId());
            } catch (RuntimeException e) {
                log.warn("notification insert failed: {} {}", entry.kind(), entry.targetId(), e);
            }
        }
    }
}
