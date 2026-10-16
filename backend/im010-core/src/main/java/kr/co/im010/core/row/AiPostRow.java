package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/**
 * 알뜰AI 글 전체 (상세 · 수정). aiSummary 는 줄바꿈으로, tags 는 '|' 로, 관련 ID 는 ',' 로 이은 문자열.
 */
public record AiPostRow(
        long id,
        String categoryCode,
        String categoryName,
        String categorySlug,
        String bgColor,
        String accentColor,
        String title,
        String slug,
        String cardSummary,
        String thumbnailUrl,
        String bodyMd,
        String bodyHtml,
        String aiSummary,
        String tags,
        String sourcesJson,
        String relatedPlanIds,
        String relatedInternetIds,
        boolean sponsored,
        boolean pinned,
        String seoTitle,
        String seoDescription,
        int readMinutes,
        String authorName,
        String status,
        OffsetDateTime publishAt,
        OffsetDateTime contentUpdatedAt,
        long viewCount,
        String createdBy,
        String updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
