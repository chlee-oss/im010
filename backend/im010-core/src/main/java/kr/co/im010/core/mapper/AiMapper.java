package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.AiCategoryRow;
import kr.co.im010.core.row.AiPostListRow;
import kr.co.im010.core.row.AiPostRow;
import kr.co.im010.core.row.AiPostWrite;

/**
 * 알뜰AI 콘텐츠 (im010_알뜰AI_기획서.md). 고객에게 보이는 글 = 노출 카테고리 · status PUBLISHED · publish_at 이 지난 글.
 * state (백오피스 목록): DRAFT 작성 중 · SCHEDULED 게시 예약 · PUBLISHED 게시 중 · HIDDEN 비노출
 */
@Mapper
public interface AiMapper {

    // ---------- 카테고리 ----------

    List<AiCategoryRow> findCategories(@Param("exposedOnly") boolean exposedOnly);

    AiCategoryRow findCategory(@Param("code") String code);

    boolean existsCategorySlug(@Param("slug") String slug, @Param("excludeCode") String excludeCode);

    void insertCategory(@Param("code") String code, @Param("name") String name, @Param("slug") String slug,
                        @Param("bgColor") String bgColor, @Param("accentColor") String accentColor,
                        @Param("sortOrder") int sortOrder, @Param("exposed") boolean exposed);

    int updateCategory(@Param("code") String code, @Param("name") String name, @Param("slug") String slug,
                       @Param("bgColor") String bgColor, @Param("accentColor") String accentColor,
                       @Param("sortOrder") int sortOrder, @Param("exposed") boolean exposed);

    // ---------- 고객 화면 ----------

    /**
     * 게시 중인 글. sort = latest | popular (최근 30일 조회 수). excludeId 글(목록 위 상단 고정 카드 · 지금 보는 글)은 뺀다.
     */
    List<AiPostListRow> findPublished(@Param("categoryCode") String categoryCode, @Param("tag") String tag,
                                      @Param("q") String q, @Param("sort") String sort,
                                      @Param("excludeId") Long excludeId,
                                      @Param("limit") int limit, @Param("offset") int offset);

    int countPublished(@Param("categoryCode") String categoryCode, @Param("tag") String tag, @Param("q") String q,
                       @Param("excludeId") Long excludeId);

    /** 상단 고정 글 (카테고리를 주지 않으면 가장 최근에 게시된 고정 글 하나) */
    AiPostListRow findPinned(@Param("categoryCode") String categoryCode);

    AiPostRow findPublishedPost(@Param("id") long id);

    /** 이전 글(더 오래된) · 다음 글(더 최근) — 같은 카테고리 */
    AiPostListRow findOlder(@Param("categoryCode") String categoryCode, @Param("publishAt") OffsetDateTime publishAt,
                            @Param("id") long id);

    AiPostListRow findNewer(@Param("categoryCode") String categoryCode, @Param("publishAt") OffsetDateTime publishAt,
                            @Param("id") long id);

    /** 게시 중인 글이면 조회 수 +1 (전체 · 일자별). 바뀐 줄 수 */
    int incrementView(@Param("id") long id);

    void incrementDailyView(@Param("id") long id, @Param("viewOn") LocalDate viewOn);

    // ---------- 백오피스 ----------

    List<AiPostListRow> findPosts(@Param("categoryCode") String categoryCode, @Param("state") String state,
                                  @Param("q") String q, @Param("limit") int limit, @Param("offset") int offset);

    int countPosts(@Param("categoryCode") String categoryCode, @Param("state") String state, @Param("q") String q);

    AiPostRow findPost(@Param("id") long id);

    long insertPost(@Param("w") AiPostWrite write);

    void updatePost(@Param("id") long id, @Param("w") AiPostWrite write);

    /** 상단 고정은 카테고리당 하나: 다른 고정 글을 해제 */
    void unpinOthers(@Param("categoryCode") String categoryCode, @Param("exceptId") long exceptId);

    void publish(@Param("id") long id, @Param("publishAt") OffsetDateTime publishAt, @Param("by") String by);

    void setStatus(@Param("id") long id, @Param("status") String status, @Param("by") String by);

    /** 게시된 적 있는 글의 본문을 고치면 "수정" 일시 갱신 */
    void touchContentUpdated(@Param("id") long id);

    void deletePost(@Param("id") long id);
}
