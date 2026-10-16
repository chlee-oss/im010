package kr.co.im010.core.row;

/** 알뜰AI 글 저장값 (백오피스 작성 · 수정). 배열 칼럼은 배열로. */
public record AiPostWrite(
        String categoryCode,
        String title,
        String slug,
        String cardSummary,
        String thumbnailUrl,
        String bodyMd,
        String bodyHtml,
        String[] aiSummary,
        String[] tags,
        String sourcesJson,
        Long[] relatedPlanIds,
        Long[] relatedInternetIds,
        boolean sponsored,
        boolean pinned,
        String seoTitle,
        String seoDescription,
        int readMinutes,
        String by
) {
}
