package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.CollectUrlRow;
import kr.co.im010.core.row.CrawlRunAdminRow;
import kr.co.im010.core.row.PartnerAdminRow;
import kr.co.im010.core.row.PartnerTabRow;

/** 제휴사관리(PA-01: 제휴사 · 수집 URL · 사이트 탭) · 스케줄관리(BA-01: 스케줄 · 즉시 실행 · 실행 이력). */
@Mapper
public interface PartnerAdminMapper {

    List<PartnerAdminRow> findPartners();

    PartnerAdminRow findPartner(@Param("code") String code);

    void insertPartner(@Param("code") String code, @Param("name") String name, @Param("chipBg") String chipBg,
                       @Param("chipFg") String chipFg, @Param("homepageUrl") String homepageUrl,
                       @Param("exposed") boolean exposed, @Param("sortOrder") int sortOrder);

    int updatePartner(@Param("code") String code, @Param("name") String name, @Param("chipBg") String chipBg,
                      @Param("chipFg") String chipFg, @Param("homepageUrl") String homepageUrl,
                      @Param("exposed") boolean exposed, @Param("sortOrder") int sortOrder);

    List<CollectUrlRow> findUrls(@Param("partnerCode") String partnerCode);

    CollectUrlRow findUrl(@Param("id") long id);

    /** 같은 URL 이 이미 등록돼 있는지: 같은 유형, 또는 후불 ↔ 선불 사이 (3.1) */
    CollectUrlRow findConflictingUrl(@Param("urlType") String urlType, @Param("url") String url,
                                     @Param("excludeId") Long excludeId);

    void insertUrl(@Param("partnerCode") String partnerCode, @Param("urlType") String urlType,
                   @Param("url") String url, @Param("label") String label, @Param("sortOrder") int sortOrder);

    void updateUrl(@Param("id") long id, @Param("urlType") String urlType, @Param("url") String url,
                   @Param("label") String label, @Param("sortOrder") int sortOrder);

    void deleteUrl(@Param("id") long id);

    int countUrls(@Param("partnerCode") String partnerCode, @Param("urlType") String urlType);

    List<PartnerTabRow> findTabs(@Param("partnerCode") String partnerCode);

    PartnerTabRow findTab(@Param("id") long id);

    void updateTabStatus(@Param("id") long id, @Param("status") String status);

    /** 탭을 수집 URL 로 등록하면 사이트 탭 목록에서는 뺀다. */
    void deleteTabByUrl(@Param("partnerCode") String partnerCode, @Param("url") String url);

    void upsertSchedule(@Param("partnerCode") String partnerCode, @Param("enabled") boolean enabled,
                        @Param("days") String days, @Param("runTime") LocalTime runTime);

    boolean existsWaitingJob(@Param("partnerCode") String partnerCode);

    void insertManualJob(@Param("partnerCode") String partnerCode, @Param("runOn") LocalDate runOn,
                         @Param("requestedBy") String requestedBy);

    List<CrawlRunAdminRow> findRuns(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                    @Param("partnerCode") String partnerCode, @Param("urlType") String urlType,
                                    @Param("result") String result, @Param("limit") int limit);
}
