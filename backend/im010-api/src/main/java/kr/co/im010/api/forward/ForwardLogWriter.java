package kr.co.im010.api.forward;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import kr.co.im010.core.mapper.ForwardLogMapper;
import kr.co.im010.core.row.ForwardLog;

/** 이동 기록은 고객을 기다리게 하지 않도록 비동기로 저장한다. 실패해도 이동은 막지 않는다. */
@Component
public class ForwardLogWriter {

    private static final Logger log = LoggerFactory.getLogger(ForwardLogWriter.class);

    private final ForwardLogMapper forwardLogMapper;

    public ForwardLogWriter(ForwardLogMapper forwardLogMapper) {
        this.forwardLogMapper = forwardLogMapper;
    }

    @Async
    public void write(ForwardLog entry) {
        try {
            forwardLogMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("forward_log insert failed: kind={} target={} result={}", entry.kind(), entry.targetId(), entry.result(), e);
        }
    }
}
