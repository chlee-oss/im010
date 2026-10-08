package kr.co.im010.batch.crawl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.im010.batch.parse.ParsedPlan;
import kr.co.im010.core.row.HandledHashRow;
import kr.co.im010.core.row.OpenItemRow;
import kr.co.im010.core.row.PlanBaselineRow;

/**
 * 변경 감지 (백오피스 6.2). DB 를 건드리지 않는 순수 계산이라 단위 테스트로 규칙을 고정한다.
 * <pre>
 * 변경 없음  수집값 = 최신 승인값                → 기록만, 최종 수집일 갱신
 * 신규       같은 요금제가 없음                  → 점검 대기
 * 변경       같은 요금제인데 값이 다름            → 점검 대기 (요금 ±30% · 0원은 ⚠)
 * 판매 재개  판매 종료된 요금제가 다시 보임        → 점검 대기 (⚠ RESUMED)
 * 사라짐     판매 종료가 아닌 요금제가 결과에 없음  → 자동 판매 종료
 * </pre>
 * 같은 요금제의 점검 건은 하나만 열어 둔다: 같은 값이면 기록만, 다른 값이면 이전 건을 대체(SUPERSEDED)한다.
 * 이미 승인된 값과 같으면 변경 없음, 제외된 값과 같으면 다시 점검 대기에 올리지 않고 표시만 한다.
 */
public final class ChangeDetector {

    /** 운영자 보완 항목(plan_version.supplemented_fields)과 같은 이름을 쓴다. */
    public static final String F_NAME = "name";
    public static final String F_DATA = "dataText";
    public static final String F_DATA_GB = "dataGb";
    public static final String F_QOS = "qos";
    public static final String F_VOICE = "voice";
    public static final String F_SMS = "sms";
    public static final String F_NETWORK = "network";
    public static final String F_GENERATION = "generation";
    public static final String F_PRICE = "price";
    public static final String F_DISCOUNT_MONTHS = "discountMonths";
    public static final String F_PRICE_AFTER = "priceAfterDiscount";

    private static final Set<String> LIVE = Set.of("PENDING", "SCHEDULED", "PUBLISHED", "HIDDEN");

    private ChangeDetector() {
    }

    public enum Change { NEW, CHANGED, UNCHANGED, ENDED }

    public enum Status { RECORDED, REVIEW_PENDING, AUTO_APPLIED }

    /**
     * 저장할 수집 건 하나. 판매 종료 건은 plan 이 null 이고 ended 에 기준값이 들어 있다.
     */
    public record Item(ParsedPlan plan, PlanBaselineRow ended, String key, Long planId, Change change, Status status,
                       List<String> changedFields, List<String> warnings, String valueHash) {
    }

    public record Result(List<Item> items, List<Long> touchedPlanIds, List<Long> endedPlanIds, List<Long> supersededItemIds) {

        public long count(Change change) {
            return items.stream().filter(i -> i.change() == change).count();
        }
    }

    public static Result detect(List<ParsedPlan> collected, List<PlanBaselineRow> baselines,
                                List<OpenItemRow> openItems, List<HandledHashRow> handled, double priceJumpRatio) {
        Map<String, PlanBaselineRow> baselineByKey = new HashMap<>();
        for (PlanBaselineRow b : baselines) {
            baselineByKey.put(key(b), b);
        }
        Map<String, OpenItemRow> openByKey = new HashMap<>();
        for (OpenItemRow o : openItems) {
            openByKey.put(o.itemKey(), o);
        }
        Set<String> approved = new HashSet<>();
        Set<String> excluded = new HashSet<>();
        for (HandledHashRow h : handled) {
            (h.status().equals("APPROVED") ? approved : excluded).add(h.itemKey() + "|" + h.valueHash());
        }

        List<Item> items = new ArrayList<>();
        List<Long> touched = new ArrayList<>();
        List<Long> superseded = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ParsedPlan p : collected) {
            String key = p.key();
            seen.add(key);
            String hash = hash(p);
            PlanBaselineRow b = baselineByKey.get(key);
            Long planId = b != null ? b.planId() : null;
            List<String> warnings = warnings(p);
            List<String> changed = List.of();
            Change change;
            if (b == null) {
                change = Change.NEW;
            } else {
                changed = diff(b, p);
                boolean resumed = "ENDED".equals(b.status());
                if (resumed) {
                    warnings.add("RESUMED");
                }
                change = changed.isEmpty() && !resumed ? Change.UNCHANGED : Change.CHANGED;
                if (changed.contains(F_PRICE) && jumped(b.price(), p.price(), priceJumpRatio)) {
                    warnings.add("PRICE_JUMP");
                }
                if (change == Change.CHANGED && !resumed && approved.contains(key + "|" + hash)) {
                    // 운영자가 값을 고쳐 승인한 요금제: 같은 수집값이 다시 들어와도 변경이 아니다
                    change = Change.UNCHANGED;
                    changed = List.of();
                }
            }

            OpenItemRow open = openByKey.get(key);
            Status status;
            if (change == Change.UNCHANGED) {
                status = Status.RECORDED;
                touched.add(planId);
                if (open != null) {
                    superseded.add(open.id());   // 사이트 값이 승인값으로 돌아옴
                }
            } else if (open != null && hash.equals(open.valueHash())) {
                status = Status.RECORDED;        // 이미 같은 값으로 점검 대기 중
            } else if (excluded.contains(key + "|" + hash)) {
                status = Status.RECORDED;
                warnings.add("SAME_AS_EXCLUDED");
                if (open != null) {
                    superseded.add(open.id());
                }
            } else {
                status = Status.REVIEW_PENDING;
                if (open != null) {
                    superseded.add(open.id());
                }
            }
            if (b != null && change != Change.UNCHANGED) {
                touched.add(planId);
            }
            items.add(new Item(p, null, key, planId, change, status, changed, warnings, hash));
        }

        List<Long> ended = new ArrayList<>();
        for (PlanBaselineRow b : baselines) {
            String key = key(b);
            if (seen.contains(key) || !LIVE.contains(b.status())) {
                continue;
            }
            ended.add(b.planId());
            items.add(new Item(null, b, key, b.planId(), Change.ENDED, Status.AUTO_APPLIED, List.of(), List.of(), null));
        }
        for (OpenItemRow o : openItems) {
            if (!seen.contains(o.itemKey()) && !superseded.contains(o.id())) {
                superseded.add(o.id());          // 점검 대기 중에 사이트에서 사라짐
            }
        }
        return new Result(items, touched, ended, superseded);
    }

    static String key(PlanBaselineRow b) {
        return b.partnerPlanCode() != null ? b.partnerPlanCode() : b.name() + "|" + b.network();
    }

    static List<String> warnings(ParsedPlan p) {
        List<String> w = new ArrayList<>();
        if (p.name() == null) {
            w.add("MISSING_NAME");
        }
        if (p.price() == null) {
            w.add("MISSING_PRICE");
        } else if (p.price() == 0) {
            w.add("PRICE_ZERO");
        }
        if (p.network() == null) {
            w.add("MISSING_NETWORK");
        }
        if (p.dataText() == null || (p.dataGb() == null && !p.dataText().contains("무제한"))) {
            w.add("MISSING_DATA");
        }
        return w;
    }

    /** 바뀐 항목. 수집되지 않았는데(null) 운영자가 보완한 항목은 비교하지 않는다 (결정 #21). */
    static List<String> diff(PlanBaselineRow b, ParsedPlan p) {
        Set<String> supplemented = b.supplementedFields() == null || b.supplementedFields().isEmpty()
                ? Set.of() : Set.of(b.supplementedFields().split("\\|"));
        List<String> changed = new ArrayList<>();
        compare(changed, supplemented, F_NAME, b.name(), p.name());
        compare(changed, supplemented, F_DATA, b.dataText(), p.dataText());
        compare(changed, supplemented, F_DATA_GB, b.dataGb(), p.dataGb());
        compare(changed, supplemented, F_QOS, b.qosText(), p.qosText());
        compare(changed, supplemented, F_VOICE, b.voiceText(), p.voiceText());
        compare(changed, supplemented, F_SMS, b.smsText(), p.smsText());
        compare(changed, supplemented, F_NETWORK, b.network(), p.network());
        compare(changed, supplemented, F_GENERATION, b.generation(), p.generation());
        compare(changed, supplemented, F_PRICE, b.price(), p.price());
        compare(changed, supplemented, F_DISCOUNT_MONTHS, b.discountMonths(), p.discountMonths());
        compare(changed, supplemented, F_PRICE_AFTER, b.priceAfterDiscount(), p.priceAfterDiscount());
        return changed;
    }

    private static void compare(List<String> changed, Set<String> supplemented, String field, Object current, Object collected) {
        if (collected == null && supplemented.contains(field)) {
            return;
        }
        boolean same = current instanceof BigDecimal c && collected instanceof BigDecimal n
                ? c.compareTo(n) == 0
                : Objects.equals(current, collected);
        if (!same) {
            changed.add(field);
        }
    }

    private static boolean jumped(Integer before, Integer after, double ratio) {
        if (before == null || after == null || before == 0) {
            return false;
        }
        return Math.abs(after - before) > before * ratio;
    }

    /** 수집값 지문: 같은 값이 다시 들어왔는지 판단한다 (순서 · URL 은 제외). */
    static String hash(ParsedPlan p) {
        String joined = String.join("\u001f",
                String.valueOf(p.name()), String.valueOf(p.dataText()),
                p.dataGb() == null ? "null" : p.dataGb().stripTrailingZeros().toPlainString(),
                String.valueOf(p.qosText()), String.valueOf(p.voiceText()), String.valueOf(p.smsText()),
                String.valueOf(p.network()), String.valueOf(p.generation()), String.valueOf(p.price()),
                String.valueOf(p.discountMonths()), String.valueOf(p.priceAfterDiscount()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
