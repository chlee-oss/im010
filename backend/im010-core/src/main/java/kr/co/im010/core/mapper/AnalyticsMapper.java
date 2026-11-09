package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.AnalyticsRows.Daily;
import kr.co.im010.core.row.AnalyticsRows.Event;
import kr.co.im010.core.row.AnalyticsRows.Interest;
import kr.co.im010.core.row.AnalyticsRows.PageStat;
import kr.co.im010.core.row.AnalyticsRows.PlanStat;
import kr.co.im010.core.row.AnalyticsRows.PostStat;
import kr.co.im010.core.row.AnalyticsRows.Visit;

/**
 * 대시보드 (DS-01 요약 · DS-02 관심사 · DS-03 콘텐츠 효과).
 * api: 이벤트 저장 · batch: 일별 집계 다시 만들기 · 원본 정리 · admin: 집계 조회.
 * 조회의 device 는 ALL · PC · MO (집계 테이블의 'ALL' 행이 합계).
 */
@Mapper
public interface AnalyticsMapper {

    // ---------- api: 수집 ----------

    void insertEvents(@Param("events") List<Event> events);

    // ---------- batch: 집계 ----------

    /** 그날 집계를 지우고 원본에서 다시 만든다 (같은 트랜잭션에서 차례로) */
    void deleteStats(@Param("day") LocalDate day);

    void aggregateVisit(@Param("day") LocalDate day);

    void aggregatePage(@Param("day") LocalDate day);

    void aggregatePlan(@Param("day") LocalDate day);

    void aggregateInterest(@Param("day") LocalDate day);

    void aggregateContent(@Param("day") LocalDate day);

    /** 원본 보관 기간이 지난 이벤트 삭제 */
    int purgeEvents(@Param("before") LocalDate before);

    // ---------- admin: 조회 ----------

    /** 기간 합계 (key = 'TOTAL') */
    Visit visitTotal(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device);

    /** 유입별 (key = SEARCH · SOCIAL · DIRECT · OTHER) */
    List<Visit> visitByRef(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device);

    /** 기기별 (key = PC · MO) */
    List<Visit> visitByDevice(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 날짜마다 한 행 (빈 날도 0) */
    List<Daily> daily(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device);

    List<PageStat> pages(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device);

    /** 요금제별 (조회 많은 순, limit 없으면 전체) */
    List<PlanStat> plans(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device,
                         @Param("limit") Integer limit);

    List<Interest> interests(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device);

    /** 알뜰AI 글별 (조회 많은 순) */
    List<PostStat> posts(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("device") String device,
                         @Param("limit") Integer limit);
}
