package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.ItemValues;
import kr.co.im010.core.row.ReviewItemRow;

/**
 * 요금제배치관리(BA-02) · 승인관리(BA-03)의 수집 건 조회 · 상태 변경.
 * view: PENDING 점검 대기 · REQUESTED 승인 요청 · UNCHANGED 변경 없음 · ENDED 판매 종료 · EXCLUDED 제외 · ALL
 * (PENDING · REQUESTED 는 수집일과 관계없이 열려 있는 건 전체, 나머지는 수집일 기준)
 */
@Mapper
public interface ReviewMapper {

    List<ReviewItemRow> findItems(@Param("view") String view, @Param("collectedOn") LocalDate collectedOn,
                                  @Param("partnerCode") String partnerCode, @Param("urlType") String urlType,
                                  @Param("limit") int limit, @Param("offset") int offset);

    int countItems(@Param("view") String view, @Param("collectedOn") LocalDate collectedOn,
                   @Param("partnerCode") String partnerCode, @Param("urlType") String urlType);

    /** 화면 상단 건수: pending · pending_new · pending_changed · requested · unchanged · ended · excluded */
    Map<String, Long> countSummary(@Param("collectedOn") LocalDate collectedOn);

    LocalDate findLatestCollectedOn();

    ReviewItemRow findItem(@Param("id") long id);

    List<ReviewItemRow> findItemsByIds(@Param("ids") List<Long> ids);

    void updateValues(@Param("id") long id, @Param("v") ItemValues values,
                      @Param("editedFields") String[] editedFields, @Param("memo") String memo);

    int markReviewed(@Param("ids") List<Long> ids, @Param("reviewer") String reviewer);

    int markExcluded(@Param("ids") List<Long> ids, @Param("reviewer") String reviewer, @Param("reason") String reason);

    int markRejected(@Param("ids") List<Long> ids, @Param("approver") String approver, @Param("reason") String reason);

    int markApproved(@Param("id") long id, @Param("approver") String approver);

    /** 자동 판매 종료 건의 [판매 재개] → 승인 요청 */
    int requestResume(@Param("id") long id, @Param("reviewer") String reviewer);

    /** [기존 요금제와 연결]: 신규 건을 이름만 바뀐 기존 요금제의 변경 건으로 (승인하면 그 요금제의 새 버전) */
    int linkToPlan(@Param("id") long id, @Param("planId") long planId);

    long countByStatus(@Param("status") String status);
}
