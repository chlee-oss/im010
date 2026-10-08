package kr.co.im010.core.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.PlanForwardTarget;
import kr.co.im010.core.row.PlanRow;

@Mapper
public interface PlanMapper {

    /** 게시 중인 요금제 목록. partnerCode 가 null 이면 전체. 가격 오름차순. */
    List<PlanRow> findPublished(@Param("planType") String planType, @Param("partnerCode") String partnerCode);

    /**
     * 계산기: 게시 중인 후불 요금제 중 망 + 기본 제공량 조건을 만족하는 최저가.
     * minGb 가 null 이면 무제한 요금제만 대상.
     */
    PlanRow findCheapestPostpaid(@Param("network") String network, @Param("minGb") BigDecimal minGb);

    /** 상세: 게시 이력이 있는 요금제 (판매 종료 포함, 비노출 · 미게시 제외). */
    PlanRow findDetail(@Param("id") long id);

    /** 비슷한 요금제: 같은 망의 게시 중 후불 요금제, 데이터 ±50% (무제한은 무제한끼리). */
    List<PlanRow> findSimilarPostpaid(@Param("excludeId") long excludeId,
                                      @Param("network") String network,
                                      @Param("dataGb") BigDecimal dataGb,
                                      @Param("limit") int limit);

    /** 이달의 요금제: 연결된 요금제가 게시 중인 항목만. 제휴사 이름순 → 제휴사 사이트 순서. */
    List<PlanRow> findMonthlyPicks(@Param("limit") int limit);

    int countPublished(@Param("planType") String planType);

    PlanForwardTarget findForwardTarget(@Param("id") long id);
}
