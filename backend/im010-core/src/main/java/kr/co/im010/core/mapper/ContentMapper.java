package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.FaqRow;
import kr.co.im010.core.row.FooterRow;
import kr.co.im010.core.row.TermsRow;

/** 프런트 고지 콘텐츠: ST-04 약관 · ST-06 Footer · ST-07 FAQ (백오피스 관리 + 고객 API 조회). */
@Mapper
public interface ContentMapper {

    // ---------- 약관 ----------

    List<TermsRow> findTerms(@Param("termsType") String termsType);

    TermsRow findTerm(@Param("id") long id);

    /** 시행 중인 버전: 게시됐고 시행일이 지난 가장 최근 버전 */
    TermsRow findCurrentTerm(@Param("termsType") String termsType, @Param("today") LocalDate today);

    /** 프런트 "이전 버전 보기" 용: 게시된 버전 (본문 제외 목록은 화면에서 거른다) */
    List<TermsRow> findPublishedTerms(@Param("termsType") String termsType);

    boolean existsTermsVersion(@Param("termsType") String termsType, @Param("version") String version,
                               @Param("excludeId") Long excludeId);

    long insertTerm(@Param("termsType") String termsType, @Param("version") String version, @Param("body") String body,
                    @Param("effectiveOn") LocalDate effectiveOn, @Param("by") String by);

    int updateDraftTerm(@Param("id") long id, @Param("version") String version, @Param("body") String body,
                        @Param("effectiveOn") LocalDate effectiveOn);

    int deleteDraftTerm(@Param("id") long id);

    int publishTerm(@Param("id") long id, @Param("by") String by);

    // ---------- Footer ----------

    FooterRow findLatestFooter();

    List<FooterRow> findFooterHistory(@Param("limit") int limit);

    void insertFooter(FooterRow footer);

    // ---------- FAQ ----------

    List<FaqRow> findFaqs(@Param("exposedOnly") boolean exposedOnly);

    FaqRow findFaq(@Param("id") long id);

    long insertFaq(@Param("category") String category, @Param("question") String question, @Param("answer") String answer,
                   @Param("sortOrder") int sortOrder, @Param("exposed") boolean exposed, @Param("by") String by);

    int updateFaq(@Param("id") long id, @Param("category") String category, @Param("question") String question,
                  @Param("answer") String answer, @Param("sortOrder") int sortOrder, @Param("exposed") boolean exposed,
                  @Param("by") String by);

    int deleteFaq(@Param("id") long id);
}
