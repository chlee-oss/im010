package kr.co.im010.admin.plan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.row.ItemValues;

/**
 * 요금제 항목 이름 (수집 건 changed_fields · edited_fields, 버전 supplemented_fields 공통 — 배치 ChangeDetector 와 같은 이름)
 * 과 입력값 검사.
 */
public final class Fields {

    public static final Set<String> NETWORKS = Set.of("SKT", "KT", "LGU");
    public static final Set<String> GENERATIONS = Set.of("LTE", "5G");

    private Fields() {
    }

    /** a → b 에서 값이 다른 항목 이름 */
    public static List<String> diff(ItemValues a, ItemValues b) {
        List<String> d = new ArrayList<>();
        add(d, "name", a.name(), b.name());
        add(d, "dataText", a.dataText(), b.dataText());
        add(d, "dataGb", a.dataGb(), b.dataGb());
        add(d, "qos", a.qosText(), b.qosText());
        add(d, "voice", a.voiceText(), b.voiceText());
        add(d, "sms", a.smsText(), b.smsText());
        add(d, "network", a.network(), b.network());
        add(d, "generation", a.generation(), b.generation());
        add(d, "price", a.price(), b.price());
        add(d, "discountMonths", a.discountMonths(), b.discountMonths());
        add(d, "priceAfterDiscount", a.priceAfterDiscount(), b.priceAfterDiscount());
        return d;
    }

    private static void add(List<String> d, String field, Object x, Object y) {
        boolean same = x instanceof BigDecimal bx && y instanceof BigDecimal by ? bx.compareTo(by) == 0 : Objects.equals(x, y);
        if (!same) {
            d.add(field);
        }
    }

    /** 화면 입력 정리 · 형식 검사 (공백 제거, 망 · 세대 코드, 음수 금지) */
    public static ItemValues clean(ItemValues v) {
        String network = Texts.trim(v.network());
        String generation = Texts.trim(v.generation());
        if (network != null && !NETWORKS.contains(network)) {
            throw ApiException.badRequest("망은 SKT · KT · LGU 중 하나여야 합니다");
        }
        if (generation != null && !GENERATIONS.contains(generation)) {
            throw ApiException.badRequest("세대는 LTE · 5G 중 하나여야 합니다");
        }
        nonNegative(v.price(), "요금");
        nonNegative(v.discountMonths(), "할인 기간");
        nonNegative(v.priceAfterDiscount(), "할인 후 요금");
        if (v.dataGb() != null && v.dataGb().signum() < 0) {
            throw ApiException.badRequest("데이터 기본 제공량은 0 이상이어야 합니다");
        }
        return new ItemValues(len(Texts.trim(v.name()), 100, "요금제명"), len(Texts.trim(v.dataText()), 100, "데이터"),
                v.dataGb(), len(Texts.trim(v.qosText()), 50, "QoS"), len(Texts.trim(v.voiceText()), 50, "통화"),
                len(Texts.trim(v.smsText()), 50, "문자"), network, generation, v.price(), v.discountMonths(),
                v.priceAfterDiscount());
    }

    /** 요금제 버전을 만들 때 꼭 있어야 하는 항목 (DB 필수 칼럼) */
    public static List<String> missingForVersion(ItemValues v) {
        List<String> m = new ArrayList<>();
        if (v.name() == null) {
            m.add("요금제명");
        }
        if (v.dataText() == null) {
            m.add("데이터");
        }
        if (v.network() == null) {
            m.add("망");
        }
        if (v.generation() == null) {
            m.add("세대");
        }
        if (v.price() == null) {
            m.add("요금");
        }
        return m;
    }

    private static void nonNegative(Integer n, String label) {
        if (n != null && n < 0) {
            throw ApiException.badRequest(label + "은(는) 0 이상이어야 합니다");
        }
    }

    private static String len(String s, int max, String label) {
        if (s != null && s.length() > max) {
            throw ApiException.badRequest(label + "은(는) " + max + "자 이하여야 합니다");
        }
        return s;
    }
}
