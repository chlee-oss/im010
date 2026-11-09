package kr.co.im010.api.analytics;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import kr.co.im010.core.mapper.AnalyticsMapper;
import kr.co.im010.core.row.AnalyticsRows.Event;

/** 통계 이벤트는 고객 응답을 기다리게 하지 않도록 비동기로 저장한다. 실패해도 버린다. */
@Component
public class AnalyticsWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsWriter.class);

    private final AnalyticsMapper mapper;

    public AnalyticsWriter(AnalyticsMapper mapper) {
        this.mapper = mapper;
    }

    @Async
    public void write(List<Event> events) {
        try {
            mapper.insertEvents(events);
        } catch (RuntimeException e) {
            log.warn("analytics_event insert failed: {} events", events.size(), e);
        }
    }
}
