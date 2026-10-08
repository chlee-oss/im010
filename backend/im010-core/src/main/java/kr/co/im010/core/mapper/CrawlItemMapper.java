package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.CrawlItemRow;
import kr.co.im010.core.row.HandledHashRow;
import kr.co.im010.core.row.MonthlyPickRow;
import kr.co.im010.core.row.OpenItemRow;
import kr.co.im010.core.row.PlanBaselineRow;
import kr.co.im010.core.row.PlanCodeRow;

/** 변경 감지 · 판매 종료 · 이달의 요금제 반영. */
@Mapper
public interface CrawlItemMapper {

    /** 제휴사 · 유형의 요금제와 최신 버전 값 (판매 종료 요금제도 판매 재개 판단을 위해 포함). */
    List<PlanBaselineRow> findBaselines(@Param("partnerCode") String partnerCode, @Param("planType") String planType);

    List<OpenItemRow> findOpenItems(@Param("partnerCode") String partnerCode, @Param("urlType") String urlType);

    List<HandledHashRow> findHandledHashes(@Param("partnerCode") String partnerCode, @Param("urlType") String urlType);

    void insertItem(CrawlItemRow item);

    void supersedeItems(@Param("ids") List<Long> ids);

    void touchCollected(@Param("planIds") List<Long> planIds, @Param("collectedOn") LocalDate collectedOn);

    void endPlans(@Param("planIds") List<Long> planIds, @Param("endedOn") LocalDate endedOn);

    /** 판매 종료된 요금제가 연결된 이달의 요금제를 내린다 (결정 #9). 내린 항목 수. */
    int hidePicksOfPlans(@Param("planIds") List<Long> planIds);

    List<MonthlyPickRow> findExposedPicks(@Param("partnerCode") String partnerCode);

    void updatePickOrder(@Param("id") long id, @Param("siteOrder") int siteOrder, @Param("collectedOn") LocalDate collectedOn);

    void hidePicks(@Param("ids") List<Long> ids);

    /** 이달의 요금제 연결용: 판매 종료가 아닌 요금제를 제휴사 요금제 코드로 찾는다. */
    List<PlanCodeRow> findPlansByCodes(@Param("partnerCode") String partnerCode, @Param("codes") List<String> codes);

    /** 보관 기간이 지난 처리 완료 수집 건 삭제 (결정 #28). */
    int deleteExpired(@Param("before") LocalDate before);
}
