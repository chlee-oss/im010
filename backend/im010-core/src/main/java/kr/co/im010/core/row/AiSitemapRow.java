package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** sitemap.xml 용 알뜰AI 글 한 줄 (고객에게 보이는 글만). contentUpdatedAt 은 본문을 고친 적이 없으면 null. */
public record AiSitemapRow(
        long id,
        String slug,
        String categorySlug,
        OffsetDateTime publishAt,
        OffsetDateTime contentUpdatedAt
) {
}
