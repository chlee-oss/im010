package kr.co.im010.admin.ai;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.content.PostBody;
import kr.co.im010.core.mapper.AiMapper;
import kr.co.im010.core.mapper.InternetProductMapper;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.AiPostListRow;
import kr.co.im010.core.row.AiPostRow;
import kr.co.im010.core.row.AiPostWrite;
import kr.co.im010.core.row.PlanRow;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알뜰AI 게시글관리 (CT-01): 작성 · 수정 · 미리보기 · 게시(즉시 · 예약) · 비노출 · 상단 고정 · 삭제.
 * 화면 상태: DRAFT 작성 중 · SCHEDULED 게시 예약 · PUBLISHED 게시 중 · HIDDEN 비노출 (게시 · 비노출 · 삭제는 승인 권한).
 */
@Service
public class AiPostAdminService {

    public static final String PROGRAM = "CT-01";
    private static final int PAGE_SIZE = 50;
    private static final Set<String> STATES = Set.of("DRAFT", "SCHEDULED", "PUBLISHED", "HIDDEN");
    private static final Pattern SLUG = Pattern.compile("^[\\p{IsHangul}a-z0-9]+(-[\\p{IsHangul}a-z0-9]+)*$");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record Source(String name, String title, String date, String url) {
    }

    public record PostRequest(String categoryCode, String title, String slug, String cardSummary, String thumbnailUrl,
                              String bodyMd, List<String> aiSummary, List<String> tags, List<Source> sources,
                              List<Long> relatedPlanIds, List<Long> relatedInternetIds, boolean sponsored, boolean pinned,
                              String seoTitle, String seoDescription) {
    }

    public record ListItem(AiPostListRow post, String state) {
    }

    public record Page(int total, int page, List<ListItem> items) {
    }

    public record RelatedOption(long id, String kind, String label, String partner, Integer price) {
    }

    /** 수정 화면: 글 + 상태 + 정리된 목록 값 */
    public record Detail(AiPostRow post, String state, List<String> aiSummary, List<String> tags, List<Source> sources,
                         List<Long> relatedPlanIds, List<Long> relatedInternetIds, List<RelatedOption> related) {
    }

    private final AiMapper aiMapper;
    private final PlanMapper planMapper;
    private final InternetProductMapper internetMapper;
    private final AuditService audit;

    public AiPostAdminService(AiMapper aiMapper, PlanMapper planMapper, InternetProductMapper internetMapper,
                              AuditService audit) {
        this.aiMapper = aiMapper;
        this.planMapper = planMapper;
        this.internetMapper = internetMapper;
        this.audit = audit;
    }

    public Page list(String categoryCode, String state, String q, int page) {
        String st = state != null && STATES.contains(state) ? state : null;
        int p = Math.max(page, 1);
        String c = Texts.trim(categoryCode);
        String query = Texts.trim(q);
        List<ListItem> items = aiMapper.findPosts(c, st, query, PAGE_SIZE, (p - 1) * PAGE_SIZE).stream()
                .map(r -> new ListItem(r, state(r.status(), r.publishAt()))).toList();
        return new Page(aiMapper.countPosts(c, st, query), p, items);
    }

    public Detail detail(long id) {
        AiPostRow r = find(id);
        List<Long> plans = ids(r.relatedPlanIds());
        List<Long> internet = ids(r.relatedInternetIds());
        List<RelatedOption> related = new ArrayList<>();
        related.addAll(relatedOptions(null).stream()
                .filter(o -> ("PLAN".equals(o.kind()) && plans.contains(o.id())) || ("INTERNET".equals(o.kind()) && internet.contains(o.id())))
                .toList());
        return new Detail(r, state(r.status(), r.publishAt()), Texts.trim(r.aiSummary()) == null ? List.of()
                : Arrays.asList(r.aiSummary().split("\n")), Texts.split(r.tags()), sources(r.sourcesJson()), plans, internet,
                related);
    }

    @Transactional
    public Detail create(PostRequest req) {
        AiPostWrite w = write(req, null);
        if (w.pinned()) {
            aiMapper.unpinOthers(w.categoryCode(), 0);   // 고정은 카테고리당 하나 (유일 인덱스보다 먼저 정리)
        }
        long id = aiMapper.insertPost(w);
        audit.action(PROGRAM, "AI_POST_CREATE", "ai_post " + id, w.title());
        return detail(id);
    }

    @Transactional
    public Detail update(long id, PostRequest req) {
        AiPostRow before = find(id);
        AiPostWrite w = write(req, id);
        if (w.pinned()) {
            aiMapper.unpinOthers(w.categoryCode(), id);   // 고정은 카테고리당 하나 (유일 인덱스보다 먼저 정리)
        }
        aiMapper.updatePost(id, w);
        if (!Objects.equals(before.bodyMd(), w.bodyMd()) || !Objects.equals(before.title(), w.title())) {
            aiMapper.touchContentUpdated(id);   // 이미 게시된 글이면 "수정" 일시 표시
        }
        audit.action(PROGRAM, "AI_POST_UPDATE", "ai_post " + id, w.title());
        return detail(id);
    }

    /** 즉시 게시 (publishAt 없음) · 게시 예약 (미래 일시). 승인 권한 */
    @Transactional
    public Detail publish(long id, OffsetDateTime publishAt) {
        CurrentAdmin.require(PROGRAM, Action.APPROVE);
        AiPostRow r = find(id);
        if (Texts.trim(r.bodyMd()) == null) {
            throw ApiException.badRequest("본문을 입력한 뒤 게시해 주세요");
        }
        OffsetDateTime at = publishAt == null ? OffsetDateTime.now() : publishAt;
        if (publishAt != null && !publishAt.isAfter(OffsetDateTime.now())) {
            throw ApiException.badRequest("게시 예약 일시는 지금 이후여야 합니다");
        }
        aiMapper.publish(id, at, CurrentAdmin.get().loginId());
        audit.action(PROGRAM, publishAt == null ? "AI_POST_PUBLISH" : "AI_POST_SCHEDULE", "ai_post " + id,
                publishAt == null ? r.title() : r.title() + " · " + publishAt);
        return detail(id);
    }

    /** 비노출 · 다시 노출(게시 중으로) · 작성 중으로 되돌리기 */
    @Transactional
    public Detail setStatus(long id, String status) {
        CurrentAdmin.require(PROGRAM, Action.APPROVE);
        AiPostRow r = find(id);
        if (!Set.of("HIDDEN", "PUBLISHED", "DRAFT").contains(status)) {
            throw ApiException.badRequest("상태가 올바르지 않습니다");
        }
        if ("PUBLISHED".equals(status) && r.publishAt() == null) {
            throw ApiException.conflict("게시한 적이 없는 글입니다. [게시]를 눌러 주세요");
        }
        aiMapper.setStatus(id, status, CurrentAdmin.get().loginId());
        audit.action(PROGRAM, "AI_POST_" + status, "ai_post " + id, r.title());
        return detail(id);
    }

    @Transactional
    public void delete(long id) {
        CurrentAdmin.require(PROGRAM, Action.DELETE);
        AiPostRow r = find(id);
        aiMapper.deletePost(id);
        audit.action(PROGRAM, "AI_POST_DELETE", "ai_post " + id, r.title());
    }

    /** 글쓰기 화면의 미리보기: 게시될 HTML 그대로 */
    public String preview(String bodyMd) {
        if (bodyMd != null && bodyMd.length() > 100_000) {
            throw ApiException.badRequest("본문이 너무 깁니다");
        }
        return PostBody.toHtml(bodyMd);
    }

    /** 관련 요금제 · 인터넷 상품 고르기: 지금 게시 · 노출 중인 상품 */
    public List<RelatedOption> relatedOptions(String q) {
        String query = Texts.trim(q);
        List<RelatedOption> out = new ArrayList<>();
        for (String type : List.of("POSTPAID", "PREPAID")) {
            for (PlanRow p : planMapper.findPublished(type, null)) {
                out.add(new RelatedOption(p.id(), "PLAN", p.name() + (type.equals("PREPAID") ? " (선불)" : ""), p.partnerName(),
                        p.monthlyPrice() != null ? p.monthlyPrice() : p.chargePrice()));
            }
        }
        internetMapper.findExposed(null).forEach(x ->
                out.add(new RelatedOption(x.id(), "INTERNET", x.name(), x.carrier(), x.monthlyPrice())));
        if (query == null) {
            return out;
        }
        String lq = query.toLowerCase();
        return out.stream().filter(o -> (o.label() + " " + o.partner()).toLowerCase().contains(lq)).limit(50).toList();
    }

    static String state(String status, OffsetDateTime publishAt) {
        if ("PUBLISHED".equals(status) && publishAt != null && publishAt.isAfter(OffsetDateTime.now())) {
            return "SCHEDULED";
        }
        return status;
    }

    private AiPostWrite write(PostRequest r, Long id) {
        String category = Texts.trim(r.categoryCode());
        if (category == null || aiMapper.findCategory(category) == null) {
            throw ApiException.badRequest("카테고리를 골라 주세요");
        }
        String title = Texts.trim(r.title());
        if (title == null || title.length() > 120) {
            throw ApiException.badRequest("제목은 1 ~ 120자여야 합니다");
        }
        String slug = Texts.trim(r.slug()) == null ? PostBody.slugify(title) : r.slug().trim().toLowerCase();
        if (slug.length() > 120 || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest("주소용 이름은 한글 · 영문 소문자 · 숫자와 '-' 만 쓸 수 있습니다");
        }
        String body = r.bodyMd() == null ? "" : r.bodyMd();
        if (body.length() > 100_000) {
            throw ApiException.badRequest("본문이 너무 깁니다 (10만 자 이하)");
        }
        String thumb = Texts.trim(r.thumbnailUrl());
        if (thumb != null && !thumb.startsWith("/uploads/") && !Texts.isHttpsUrl(thumb)) {
            throw ApiException.badRequest("썸네일은 업로드한 이미지 또는 https 주소여야 합니다");
        }
        List<String> summary = lines(r.aiSummary(), 3, 80, "AI 3줄 요약");
        List<String> tags = lines(r.tags(), 6, 20, "태그").stream().map(t -> t.replace("#", "")).filter(t -> !t.isBlank())
                .distinct().toList();
        if (tags.stream().anyMatch(t -> t.contains("|"))) {
            throw ApiException.badRequest("태그에 '|' 는 쓸 수 없습니다");
        }
        List<Source> sources = r.sources() == null ? List.of() : r.sources().stream()
                .filter(s -> Texts.trim(s.name()) != null || Texts.trim(s.url()) != null)
                .map(s -> new Source(len(s.name(), 50, "출처 매체"), len(s.title(), 120, "출처 제목"), len(s.date(), 20, "출처 날짜"),
                        Texts.trim(s.url()) == null ? null : Texts.requireHttpsUrl(s.url(), "출처 주소")))
                .toList();
        if (sources.size() > 5) {
            throw ApiException.badRequest("출처는 5개까지입니다");
        }
        List<Long> plans = r.relatedPlanIds() == null ? List.of() : r.relatedPlanIds().stream().distinct().toList();
        List<Long> internet = r.relatedInternetIds() == null ? List.of() : r.relatedInternetIds().stream().distinct().toList();
        if (plans.size() > 3 || internet.size() > 2) {
            throw ApiException.badRequest("관련 요금제는 3개, 인터넷 상품은 2개까지입니다");
        }
        String html = PostBody.toHtml(body);
        String sourcesJson = JSON.writeValueAsString(sources);
        return new AiPostWrite(category, title, slug, len(r.cardSummary(), 200, "카드 요약"), thumb, body, html,
                summary.toArray(String[]::new), tags.toArray(String[]::new), sourcesJson, plans.toArray(Long[]::new),
                internet.toArray(Long[]::new), r.sponsored(), r.pinned(), len(r.seoTitle(), 120, "검색용 제목"),
                len(r.seoDescription(), 200, "검색용 설명"), PostBody.readMinutes(html), CurrentAdmin.get().loginId());
    }

    private static List<String> lines(List<String> raw, int max, int maxLen, String label) {
        List<String> list = raw == null ? List.of() : raw.stream().map(Texts::trim).filter(Objects::nonNull).toList();
        if (list.size() > max || list.stream().anyMatch(s -> s.length() > maxLen)) {
            throw ApiException.badRequest(label + "은(는) " + max + "개까지, 각 " + maxLen + "자 이하여야 합니다");
        }
        return list;
    }

    private static String len(String s, int max, String label) {
        String t = Texts.trim(s);
        if (t != null && t.length() > max) {
            throw ApiException.badRequest(label + "은(는) " + max + "자 이하여야 합니다");
        }
        return t;
    }

    static List<Long> ids(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).toList();
    }

    static List<Source> sources(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : JSON.readValue(json, new TypeReference<List<Source>>() { });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private AiPostRow find(long id) {
        AiPostRow r = aiMapper.findPost(id);
        if (r == null) {
            throw ApiException.notFound("글");
        }
        return r;
    }
}
