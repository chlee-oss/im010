package kr.co.im010.batch.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 제휴사 요금제 페이지 파서 (partner.parser = default).
 * 7개 제휴사가 같은 플랫폼(rate_plan.do)이고 서버가 HTML 을 그대로 내려주므로 Jsoup 으로 읽는다.
 * <pre>
 * 카드형  li.card_list_item  > a.card_rate_link  — p.title 앞 span 에 망, ul.desc 3줄, div.price
 * 목록형  li.board_list_item > a.rate_link       — 망 배지(badge_KTF · badge_LGT · badge_SKT), li.data 에 일 데이터 · QoS
 * </pre>
 * 할인 전 가격(p.origin)과 안내 문구(p.ref)는 요금에서 뺀다.
 */
@Component
public class RatePlanParser {

    private static final Pattern TAB_HREF = Pattern.compile("rate_plan\\.do(;jsessionid=[^?]*)?\\?type=(T\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECORATION = Pattern.compile("^\\[[^\\]가-힣0-9]*\\]\\s*");
    private static final Pattern FIVE_G = Pattern.compile("(?<![0-9.])5G(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);
    private static final String NETWORK_BADGES = ".badge_SKT, .badge_KT, .badge_KTF, .badge_LGT, .badge_LGU";

    /**
     * @param tabLabel 수집 URL 에 등록한 탭 이름 (세대 판단 보조, 없으면 null)
     */
    public ParsedPage parse(Document doc, String sourceUrl, String tabLabel) {
        List<ParsedPlan> plans = new ArrayList<>();
        int order = 0;
        for (Element card : doc.select("li.card_list_item, li.board_list_item")) {
            plans.add(parseCard(card, sourceUrl, tabLabel, ++order));
        }
        return new ParsedPage(plans, tabs(doc));
    }

    private ParsedPlan parseCard(Element card, String sourceUrl, String tabLabel, int order) {
        Element link = card.selectFirst("a[href*=rateplan_view.do]");
        String detailUrl = link != null ? Urls.clean(link.absUrl("href")) : null;
        String code = Urls.param(detailUrl, "no");

        Element title = card.selectFirst("p.title");
        String networkText = null;
        String name = null;
        if (title != null) {
            Element title2 = title.clone();
            Element span = title2.selectFirst("span.text_light_color");
            if (span != null) {
                networkText = span.text();
                span.remove();
            }
            name = cleanName(title2.text());
        }
        if (networkText == null) {
            Element badge = card.selectFirst(NETWORK_BADGES);
            if (badge != null) {
                networkText = badge.text().isBlank() ? badge.className() : badge.text();
            }
        }

        // 데이터는 한 줄("11GB + 일 2GB + 3Mbps") 또는 월 · 일 두 줄("월 데이터 없음" / "일 데이터 5GB")로 나온다
        List<String> dataLines = new ArrayList<>();
        String voiceRaw = null;
        String smsRaw = null;
        for (Element li : card.select("ul.desc > li")) {
            String t = li.text();
            if (t.contains("통화") || t.startsWith("음성")) {
                voiceRaw = voiceRaw == null ? t : voiceRaw;
            } else if (t.startsWith("문자")) {
                smsRaw = smsRaw == null ? t : smsRaw;
            } else if (!t.isBlank()) {
                dataLines.add(t);
            }
        }
        SpecText.Data data = SpecText.data(dataLines.isEmpty() ? null : String.join(" + ", dataLines));
        String qos = data.qos();
        if (qos == null) {
            qos = SpecText.qosIn(name);
        }
        if (qos == null) {
            Element note = card.selectFirst(".detail .ref");
            qos = SpecText.qosIn(note != null ? note.text() : null);
        }

        Integer price = null;
        SpecText.Discount discount = null;
        Element priceBox = card.selectFirst("div.price");
        if (priceBox != null) {
            Element box = priceBox.clone();
            Element ref = box.selectFirst(".ref");
            String refText = ref != null ? ref.text() : null;
            box.select(".origin, .ref").remove();
            price = SpecText.won(box.text());
            discount = SpecText.discount(refText, price);
        }

        return new ParsedPlan(
                code,
                name,
                network(networkText),
                generation(name, tabLabel),
                data.text(),
                data.gb(),
                qos,
                SpecText.voice(voiceRaw),
                SpecText.sms(smsRaw),
                price,
                discount != null ? discount.months() : null,
                discount != null ? discount.price() : null,
                detailUrl,
                sourceUrl,
                order);
    }

    private List<ParsedPage.Tab> tabs(Document doc) {
        Map<String, ParsedPage.Tab> tabs = new LinkedHashMap<>();
        for (Element a : doc.select("a[href*=rate_plan.do]")) {
            String href = a.attr("href");
            if (!TAB_HREF.matcher(href).find()) {
                continue;
            }
            String url = Urls.clean(a.absUrl("href"));
            String label = a.text().trim();
            if (url != null && !label.isEmpty()) {
                tabs.putIfAbsent(url, new ParsedPage.Tab(url, label));
            }
        }
        return List.copyOf(tabs.values());
    }

    /** 앞에 붙은 장식 배지 ([🏆BEST] · [🔥HOT])만 뺀다. [24개월] · [평생할인] 처럼 글자 · 숫자가 있으면 이름의 일부로 둔다. */
    static String cleanName(String raw) {
        String s = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        String prev;
        do {
            prev = s;
            s = DECORATION.matcher(s).replaceFirst("");
        } while (!s.equals(prev));
        return s.isEmpty() ? null : s;
    }

    static String network(String text) {
        if (text == null) {
            return null;
        }
        String t = text.toUpperCase().replace(" ", "");
        if (t.contains("LG") || t.contains("U+")) {
            return "LGU";
        }
        if (t.contains("SK")) {
            return "SKT";
        }
        if (t.contains("KT")) {
            return "KT";
        }
        return null;
    }

    /** "5G" 표기로 판단한다. "매일 5GB" 의 5GB 는 데이터량이라 제외. */
    static String generation(String name, String tabLabel) {
        boolean fiveG = (name != null && FIVE_G.matcher(name).find())
                || (tabLabel != null && FIVE_G.matcher(tabLabel).find());
        return fiveG ? "5G" : "LTE";
    }
}
