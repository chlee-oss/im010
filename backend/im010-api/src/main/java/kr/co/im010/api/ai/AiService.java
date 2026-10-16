package kr.co.im010.api.ai;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.api.ai.AiDtos.Card;
import kr.co.im010.api.ai.AiDtos.Category;
import kr.co.im010.api.ai.AiDtos.Detail;
import kr.co.im010.api.ai.AiDtos.Page;
import kr.co.im010.api.ai.AiDtos.Post;
import kr.co.im010.api.ai.AiDtos.Source;
import kr.co.im010.api.internet.InternetProduct;
import kr.co.im010.api.plan.PlanSummary;
import kr.co.im010.api.web.NotFoundException;
import kr.co.im010.core.mapper.AiMapper;
import kr.co.im010.core.mapper.InternetProductMapper;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.AiCategoryRow;
import kr.co.im010.core.row.AiPostRow;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알뜰AI 목록 · 상세 (im010_알뜰AI_기획서.md 2 · 3장). 고객에게는 게시 중인 글 · 노출 카테고리만 보인다.
 * 관련 요금제 · 인터넷 상품은 글에 적힌 값이 아니라 지금 게시 · 노출 중인 상품만 지금 가격으로 보여 준다.
 */
@Service
public class AiService {

    static final int PAGE_SIZE = 20;
    static final int SAME_CATEGORY = 4;
    private static final Set<String> SORTS = Set.of("latest", "popular");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AiMapper aiMapper;
    private final PlanMapper planMapper;
    private final InternetProductMapper internetMapper;

    public AiService(AiMapper aiMapper, PlanMapper planMapper, InternetProductMapper internetMapper) {
        this.aiMapper = aiMapper;
        this.planMapper = planMapper;
        this.internetMapper = internetMapper;
    }

    public List<Category> categories() {
        return aiMapper.findCategories(true).stream().map(Category::of).toList();
    }

    /** @param categorySlug 없으면 전체. 상단 고정 글은 첫 페이지 · 검색 · 태그가 아닐 때만 따로 준다 */
    public Page list(String categorySlug, String tag, String q, String sort, int page) {
        String code = categoryCode(categorySlug);
        String t = blank(tag);
        String query = blank(q);
        if (query != null && query.length() < 2) {
            throw new IllegalArgumentException("검색어는 2글자 이상");
        }
        String s = sort != null && SORTS.contains(sort) ? sort : "latest";
        int p = Math.max(page, 1);
        boolean withPinned = t == null && query == null && "latest".equals(s);
        var pinnedRow = withPinned ? aiMapper.findPinned(code) : null;
        // 위에 크게 보여 준 글(첫 페이지)은 목록에서 뺀다. 다음 페이지도 같은 기준이어야 순서가 밀리지 않는다
        Long excludeId = pinnedRow == null ? null : pinnedRow.id();
        List<Card> items = aiMapper.findPublished(code, t, query, s, excludeId, PAGE_SIZE, (p - 1) * PAGE_SIZE).stream()
                .map(Card::of).toList();
        int total = aiMapper.countPublished(code, t, query, excludeId);
        return new Page(pinnedRow != null && p == 1 ? Card.of(pinnedRow) : null, total, p, PAGE_SIZE, items);
    }

    public Detail detail(long id) {
        AiPostRow r = aiMapper.findPublishedPost(id);
        if (r == null) {
            throw new NotFoundException("ai post " + id);
        }
        List<Long> planIds = ids(r.relatedPlanIds());
        List<PlanSummary> plans = planIds.isEmpty() ? List.of()
                : planMapper.findPublishedByIds(planIds).stream().map(PlanSummary::from).toList();
        List<Long> internetIds = ids(r.relatedInternetIds());
        String from = "AI_" + id;
        List<InternetProduct> internet = internetIds.isEmpty() ? List.of()
                : internetMapper.findExposed(null).stream()
                        .filter(x -> internetIds.contains(x.id()))
                        .map(InternetProduct::from)
                        .map(x -> new InternetProduct(x.id(), x.carrier(), x.carrierLabel(), x.productType(), x.name(),
                                x.monthlyPrice(), x.benefits(), "/go/internet/" + x.id() + "?from=" + from))
                        .toList();
        var older = aiMapper.findOlder(r.categoryCode(), r.publishAt(), id);
        var newer = aiMapper.findNewer(r.categoryCode(), r.publishAt(), id);
        List<Card> same = aiMapper.findPublished(r.categoryCode(), null, null, "latest", id, SAME_CATEGORY, 0)
                .stream().map(Card::of).toList();
        Post post = new Post(r.id(), r.categorySlug(), r.categoryName(), r.bgColor(), r.accentColor(), r.title(), r.slug(),
                r.cardSummary(), r.thumbnailUrl(), r.bodyHtml(), AiDtos.split(r.aiSummary(), "\n"),
                AiDtos.split(r.tags(), "\\|"), sources(r.sourcesJson()), r.sponsored(), r.readMinutes(), r.authorName(),
                r.publishAt(), r.contentUpdatedAt(), r.seoTitle(), r.seoDescription());
        return new Detail(post, plans, internet, older == null ? null : Card.of(older),
                newer == null ? null : Card.of(newer), same);
    }

    /** 조회 집계 (봇 제외 · 같은 브라우저 하루 1회는 화면에서 거른다) */
    @Transactional
    public void view(long id, String userAgent) {
        if (userAgent == null || userAgent.isBlank() || userAgent.toLowerCase().matches(".*(bot|crawl|spider|slurp|preview).*")) {
            return;
        }
        if (aiMapper.incrementView(id) > 0) {
            aiMapper.incrementDailyView(id, LocalDate.now(KST));
        }
    }

    private String categoryCode(String slug) {
        String s = blank(slug);
        if (s == null || "all".equals(s)) {
            return null;
        }
        return aiMapper.findCategories(true).stream().filter(c -> c.slug().equals(s)).map(AiCategoryRow::code)
                .findFirst().orElseThrow(() -> new NotFoundException("ai category " + s));
    }

    static List<Long> ids(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).toList();
    }

    static List<Source> sources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Source>>() { });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
