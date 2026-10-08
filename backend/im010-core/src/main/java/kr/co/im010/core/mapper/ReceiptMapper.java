package kr.co.im010.core.mapper;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.CountRow;
import kr.co.im010.core.row.ReceiptRow;

/**
 * 접수관리: RC-01 알뜰폰접수신청 (kind = PLAN, 개통하기) · RC-02 인터넷접수신청 (kind = INTERNET, 상담 신청 이동).
 * 개인정보는 없다. partner = 제휴사 코드(PLAN) 또는 제휴업체 ID(INTERNET), category = 후불 · 선불 또는 단독 · 결합.
 */
@Mapper
public interface ReceiptMapper {

    List<ReceiptRow> findReceipts(@Param("kind") String kind, @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to, @Param("partner") String partner,
                                  @Param("category") String category, @Param("fromPage") String fromPage,
                                  @Param("result") String result, @Param("limit") int limit, @Param("offset") int offset);

    int countReceipts(@Param("kind") String kind, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                      @Param("partner") String partner, @Param("category") String category,
                      @Param("fromPage") String fromPage, @Param("result") String result);

    /** 기간 안의 제휴사(업체)별 건수 — 정산 대조용 */
    List<CountRow> countByPartner(@Param("kind") String kind, @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to);

    long countSince(@Param("kind") String kind, @Param("since") OffsetDateTime since);
}
