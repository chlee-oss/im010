package kr.co.im010.api.seo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import kr.co.im010.api.config.Im010Properties;
import kr.co.im010.core.mapper.AiMapper;
import kr.co.im010.core.mapper.ContentMapper;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.AiCategoryRow;
import kr.co.im010.core.row.AiSitemapRow;
import kr.co.im010.core.row.FaqRow;
import kr.co.im010.core.row.PlanRow;

/**
 * sitemap.xml (https://www.sitemaps.org/protocol.html) — 요청할 때마다 DB 기준으로 만든다 (게시 · 판매 종료 즉시 반영).
 * 담는 주소: 메인 · FAQ · 알뜰AI 목록 · 노출 카테고리 · 고객에게 보이는 글 · 게시 중 요금제(후불 · 선불).
 * 판매 종료 · 숨김 요금제와 게시 전 글은 넣지 않는다. 주소 모양은 프런트 미리 렌더링(frontend/web/scripts/prerender.mjs)의 canonical 과 같다.
 */
@Service
public class SitemapService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    record Url(String path, LocalDate lastmod) {
    }

    private final PlanMapper planMapper;
    private final AiMapper aiMapper;
    private final ContentMapper contentMapper;
    private final String siteUrl;

    public SitemapService(PlanMapper planMapper, AiMapper aiMapper, ContentMapper contentMapper, Im010Properties props) {
        this.planMapper = planMapper;
        this.aiMapper = aiMapper;
        this.contentMapper = contentMapper;
        this.siteUrl = props.frontBaseUrl();
    }

    public String xml() {
        StringBuilder sb = new StringBuilder(8192)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Url u : urls()) {
            sb.append("  <url><loc>").append(escape(siteUrl + u.path())).append("</loc>");
            if (u.lastmod() != null) {
                sb.append("<lastmod>").append(u.lastmod()).append("</lastmod>");
            }
            sb.append("</url>\n");
        }
        return sb.append("</urlset>\n").toString();
    }

    List<Url> urls() {
        List<PlanRow> plans = Stream.concat(planMapper.findPublished("POSTPAID", null).stream(),
                planMapper.findPublished("PREPAID", null).stream()).toList();
        List<AiSitemapRow> posts = aiMapper.findSitemapEntries();
        List<AiCategoryRow> categories = aiMapper.findCategories(true);
        LocalDate latestPlan = plans.stream().map(PlanRow::collectedOn).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        LocalDate latestFaq = contentMapper.findFaqs(true).stream().map(FaqRow::updatedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).map(SitemapService::day).orElse(null);

        List<Url> urls = new ArrayList<>();
        urls.add(new Url("/", max(latestPlan, posts.isEmpty() ? null : lastmod(posts.getFirst()))));
        urls.add(new Url("/faq", latestFaq));
        urls.add(new Url("/ai", posts.stream().map(SitemapService::lastmod).max(Comparator.naturalOrder()).orElse(null)));
        for (AiCategoryRow c : categories) {
            urls.add(new Url("/ai/" + encode(c.slug()), posts.stream().filter(p -> c.slug().equals(p.categorySlug()))
                    .map(SitemapService::lastmod).max(Comparator.naturalOrder()).orElse(null)));
        }
        for (AiSitemapRow p : posts) {
            urls.add(new Url("/ai/posts/" + p.id() + "/" + encode(p.slug()), lastmod(p)));
        }
        for (PlanRow p : plans) {
            urls.add(new Url("/plans/" + p.id(), p.collectedOn()));
        }
        return urls;
    }

    private static LocalDate lastmod(AiSitemapRow p) {
        return day(p.contentUpdatedAt() != null ? p.contentUpdatedAt() : p.publishAt());
    }

    private static LocalDate day(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toLocalDate();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    /** 브라우저 encodeURIComponent 와 같은 규칙 (프런트 canonical 과 글자 하나까지 같게) */
    static String encode(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-_.!~*'()".indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return sb.toString();
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
