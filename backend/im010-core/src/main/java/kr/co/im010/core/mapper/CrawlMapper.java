package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.CollectUrlRow;
import kr.co.im010.core.row.CrawlJobRow;
import kr.co.im010.core.row.CrawlRunRow;
import kr.co.im010.core.row.PartnerTabRow;

/** 수집 URL · 미등록 탭 · 스케줄 · 작업 큐 · 실행 이력. */
@Mapper
public interface CrawlMapper {

    List<CollectUrlRow> findCollectUrls(@Param("partnerCode") String partnerCode);

    List<PartnerTabRow> findTabs(@Param("partnerCode") String partnerCode);

    void insertTab(@Param("partnerCode") String partnerCode, @Param("url") String url,
                   @Param("label") String label, @Param("seenOn") LocalDate seenOn);

    void touchTab(@Param("id") long id, @Param("label") String label, @Param("seenOn") LocalDate seenOn);

    /** 오늘 실행 시각이 지난 스케줄을 작업 큐에 넣는다 (제휴사당 하루 1건). dow = MON ... SUN */
    int enqueueScheduled(@Param("runOn") LocalDate runOn, @Param("dow") String dow, @Param("now") LocalTime now);

    void insertJob(@Param("partnerCode") String partnerCode, @Param("trigger") String trigger,
                   @Param("urlTypes") String[] urlTypes, @Param("attempt") int attempt,
                   @Param("runOn") LocalDate runOn, @Param("delayMinutes") int delayMinutes,
                   @Param("requestedBy") String requestedBy);

    /** 실행할 작업 하나를 RUNNING 으로 가져온다. 같은 제휴사 작업이 돌고 있으면 건너뛴다. */
    CrawlJobRow claimNextJob();

    void finishJob(@Param("id") long id);

    /** 비정상 종료로 RUNNING 에 남은 작업을 다시 대기열로 돌린다. */
    int requeueStaleJobs(@Param("olderThanMinutes") int olderThanMinutes);

    long insertRun(CrawlRunRow run);

    /** 직전 정상 수집 건수 (판매 종료 보호 조건 — 50% 넘게 감소). 없으면 null. */
    Integer findLastSuccessCount(@Param("partnerCode") String partnerCode, @Param("urlType") String urlType);
}
