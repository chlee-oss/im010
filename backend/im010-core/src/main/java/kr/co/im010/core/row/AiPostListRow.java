package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 알뜰AI 글 목록 한 줄 (고객 목록 카드 · 백오피스 목록). tags 는 '|' 로 이은 문자열. */
public record AiPostListRow(
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
        String tags,
        boolean sponsored,
        boolean pinned,
        int readMinutes,
        String status,
        OffsetDateTime publishAt,
        long viewCount,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
