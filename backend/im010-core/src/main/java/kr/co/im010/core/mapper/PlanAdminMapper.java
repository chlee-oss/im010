package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.ItemValues;
import kr.co.im010.core.row.NewPlanVersion;
import kr.co.im010.core.row.PlanAdminRow;
import kr.co.im010.core.row.PlanVersionRow;

/**
 * 승인관리(BA-03)의 요금제 · 버전 생성과 요금제관리(PR-01)의 보완 · 게시 예약 · 게시 · 롤백.
 * 대기 버전(draft) = 게시되지 않았고 대체되지도 않은 가장 최근 버전. 게시 예약이면 publish_at 이 있다.
 */
@Mapper
public interface PlanAdminMapper {

    /**
     * @param state PUBLISHED 게시 중(대기 버전 없음) · DRAFT 게시 대기 · SCHEDULED 게시 예약 · ENDED · HIDDEN · null 전체
     */
    List<PlanAdminRow> findPlans(@Param("planType") String planType, @Param("partnerCode") String partnerCode,
                                 @Param("state") String state, @Param("network") String network, @Param("q") String q,
                                 @Param("limit") int limit, @Param("offset") int offset);

    int countPlans(@Param("planType") String planType, @Param("partnerCode") String partnerCode,
                   @Param("state") String state, @Param("network") String network, @Param("q") String q);

    PlanAdminRow findPlan(@Param("id") long id);

    List<PlanAdminRow> findPlansByIds(@Param("ids") List<Long> ids);

    Long findPlanIdByCode(@Param("partnerCode") String partnerCode, @Param("planType") String planType,
                          @Param("code") String code);

    /** 이달의 요금제 연결용: 유형과 관계없이 판매 종료가 아닌 요금제. */
    Long findLivePlanIdByCode(@Param("partnerCode") String partnerCode, @Param("code") String code);

    long insertPlan(@Param("partnerCode") String partnerCode, @Param("planType") String planType,
                    @Param("code") String code, @Param("activationUrl") String activationUrl);

    PlanVersionRow findVersion(@Param("id") long id);

    /** 대체되지 않은 최신 버전 (게시 중이거나 대기 중). */
    PlanVersionRow findLatestVersion(@Param("planId") long planId);

    PlanVersionRow findDraft(@Param("planId") long planId);

    List<PlanVersionRow> findVersions(@Param("planId") long planId);

    int nextVersionNo(@Param("planId") long planId);

    long insertVersion(NewPlanVersion version);

    /** 게시 전 버전을 대체 처리. 예약돼 있던 버전 수를 돌려준다. */
    int countScheduledDrafts(@Param("planId") long planId);

    void discardDrafts(@Param("planId") long planId);

    void updateDraftValues(@Param("id") long id, @Param("v") ItemValues values,
                           @Param("monthlyPrice") Integer monthlyPrice, @Param("chargePrice") Integer chargePrice,
                           @Param("validDays") Integer validDays, @Param("tags") String[] tags,
                           @Param("supplemented") String[] supplemented);

    void setStatus(@Param("planId") long planId, @Param("status") String status);

    /** 판매 재개 · 비노출 해제: 게시된 버전이 있으면 게시 중, 없으면 게시 대기. */
    void restoreStatus(@Param("planId") long planId);

    void setActivationUrlIfEmpty(@Param("planId") long planId, @Param("url") String url);

    void updateActivationUrl(@Param("planId") long planId, @Param("url") String url);

    void scheduleVersion(@Param("versionId") long versionId, @Param("publishAt") OffsetDateTime publishAt);

    void cancelSchedule(@Param("versionId") long versionId);

    /** 게시: 버전에 게시 일시 기록 → 요금제의 게시 중 버전 교체. 비노출 요금제는 비노출 유지. */
    void markVersionPublished(@Param("versionId") long versionId, @Param("by") String by);

    void pointToVersion(@Param("planId") long planId, @Param("versionId") long versionId);

    /** 예약 시각이 지난 게시 예약 버전 (배치가 게시). */
    List<PlanVersionRow> findDueVersions();

    /** 롤백: 되돌린 버전은 대체 처리해 변경 감지 기준에서 뺀다. */
    void discardVersion(@Param("versionId") long versionId);

    /** 롤백 대상: 지금 게시 중인 버전보다 앞서 게시됐던 버전. */
    PlanVersionRow findPreviousPublished(@Param("planId") long planId, @Param("beforeVersionId") long beforeVersionId);

    int countScheduledOn(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    boolean existsExposedPick(@Param("planId") long planId);

    void insertMonthlyPick(@Param("partnerCode") String partnerCode, @Param("planId") long planId,
                           @Param("siteOrder") int siteOrder, @Param("code") String code,
                           @Param("itemId") long itemId, @Param("collectedOn") LocalDate collectedOn);

    /** 수집 URL 유형을 모두 지울 때: 그 유형의 게시 중 요금제를 비노출로. */
    int hidePublished(@Param("partnerCode") String partnerCode, @Param("planType") String planType);
}
