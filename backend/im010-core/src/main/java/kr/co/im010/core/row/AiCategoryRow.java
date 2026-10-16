package kr.co.im010.core.row;

/** 알뜰AI 카테고리. postCount = 이 카테고리의 글 수 (모든 상태). */
public record AiCategoryRow(
        String code,
        String name,
        String slug,
        String bgColor,
        String accentColor,
        int sortOrder,
        boolean exposed,
        int postCount
) {
}
