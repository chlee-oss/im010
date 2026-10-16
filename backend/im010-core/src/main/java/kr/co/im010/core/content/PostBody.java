package kr.co.im010.core.content;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * 알뜰AI 본문 처리: 마크다운 → HTML (원문에 쓴 HTML 태그는 글자로 처리) → 허용 태그만 남기기,
 * 외부 링크는 새 창 · noopener, 이미지는 지연 로딩. 읽는 시간 · 주소용 slug 도 여기서 만든다.
 */
public final class PostBody {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), AutolinkExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true).build();
    /** 사이트 안 상대 주소(/uploads/…)를 지우지 않도록 기준 주소를 주고 상대 링크를 유지한다 */
    private static final String BASE = "https://www.im010.co.kr";
    private static final Safelist SAFELIST = Safelist.relaxed()
            .removeTags("div", "span", "small", "sub", "sup", "cite", "colgroup", "col", "dl", "dt", "dd")
            .addAttributes("th", "align").addAttributes("td", "align")
            .preserveRelativeLinks(true);
    private static final int CHARS_PER_MINUTE = 500;   // 한국어 기준 대략

    private PostBody() {
    }

    public static String toHtml(String markdown) {
        String html = RENDERER.render(PARSER.parse(markdown == null ? "" : markdown));
        String clean = Jsoup.clean(html, BASE, SAFELIST);
        Document doc = Jsoup.parseBodyFragment(clean, BASE);
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (href.startsWith("http://") || href.startsWith("https://")) {
                a.attr("target", "_blank").attr("rel", "noopener noreferrer nofollow");
            }
        }
        for (Element img : doc.select("img")) {
            img.attr("loading", "lazy");
        }
        doc.outputSettings().prettyPrint(false);
        return doc.body().html();
    }

    /** 본문 글자 수로 읽는 시간 (분, 최소 1) */
    public static int readMinutes(String html) {
        int chars = Jsoup.parse(html == null ? "" : html).text().replaceAll("\\s+", "").length();
        return Math.max(1, (int) Math.ceil(chars / (double) CHARS_PER_MINUTE));
    }

    /** 제목 → 주소용 이름: 한글 · 영문 · 숫자만 남기고 나머지는 '-' (최대 60자) */
    public static String slugify(String title) {
        String s = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.length() > 60) {
            s = s.substring(0, 60).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "post" : s;
    }
}
