package kr.co.im010.api.ai;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import kr.co.im010.api.internet.InternetProduct;
import kr.co.im010.api.plan.PlanSummary;
import kr.co.im010.core.row.AiCategoryRow;
import kr.co.im010.core.row.AiPostListRow;

/** 알뜰AI 고객 API 응답. */
public final class AiDtos {

    private AiDtos() {
    }

    public record Category(String code, String name, String slug, String bgColor, String accentColor) {

        static Category of(AiCategoryRow c) {
            return new Category(c.code(), c.name(), c.slug(), c.bgColor(), c.accentColor());
        }
    }

    /** 목록 카드 */
    public record Card(long id, String categorySlug, String categoryName, String bgColor, String accentColor, String title,
                       String slug, String cardSummary, String thumbnailUrl, List<String> tags, boolean sponsored,
                       int readMinutes, OffsetDateTime publishedAt, boolean isNew) {

        static Card of(AiPostListRow r) {
            boolean fresh = r.publishAt() != null && r.publishAt().isAfter(OffsetDateTime.now().minusDays(3));
            return new Card(r.id(), r.categorySlug(), r.categoryName(), r.bgColor(), r.accentColor(), r.title(), r.slug(),
                    r.cardSummary(), r.thumbnailUrl(), split(r.tags(), "\\|"), r.sponsored(), r.readMinutes(), r.publishAt(),
                    fresh);
        }
    }

    public record Page(Card pinned, int total, int page, int size, List<Card> items) {
    }

    public record Source(String name, String title, String date, String url) {
    }

    public record Post(long id, String categorySlug, String categoryName, String bgColor, String accentColor, String title,
                       String slug, String cardSummary, String thumbnailUrl, String bodyHtml, List<String> aiSummary,
                       List<String> tags, List<Source> sources, boolean sponsored, int readMinutes, String authorName,
                       OffsetDateTime publishedAt, OffsetDateTime contentUpdatedAt, String seoTitle, String seoDescription) {
    }

    public record Detail(Post post, List<PlanSummary> relatedPlans, List<InternetProduct> relatedInternet, Card older,
                         Card newer, List<Card> sameCategory) {
    }

    static List<String> split(String joined, String regex) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(regex)).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
