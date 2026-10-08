package kr.co.im010.batch.parse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 요금제 카드 문구 해석. 제휴사마다 표기가 조금씩 달라 정규화한다.
 * <pre>
 * 데이터  "월 데이터 7 GB" · "11GB + 일 2GB + 3Mbps" · "300MB + 3Mbps" · "0.7GB"
 * 통화    "음성통화 무제한" · "통화 기본제공" · "음성통화 100 분" · "음성통화 없음"
 * 문자    "문자 무제한" · "문자 50 건"
 * 요금    "월 7,020 원"
 * 할인    "*7개월 이후 18,900원" · "*8개월 차부터 19,800원" · "*평생할인"
 * </pre>
 */
public final class SpecText {

    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(GB|MB|G|M)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEED = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([MK])bps", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAILY = Pattern.compile("^(?:매일|일)\\s*(\\d+(?:\\.\\d+)?)\\s*(GB|MB|G|M)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WON = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");
    private static final Pattern COUNT = Pattern.compile("(\\d[\\d,]*)\\s*(분|건)");
    private static final Pattern AFTER = Pattern.compile("(\\d+)\\s*개월\\s*이후\\s*([\\d,]+)\\s*원");
    private static final Pattern FROM = Pattern.compile("(\\d+)\\s*개월\\s*차부터\\s*([\\d,]+)\\s*원");
    private static final Pattern SPEED_AFTER_QUOTA = Pattern.compile("소진\\s*(?:후|이후)[^0-9]{0,10}(\\d+(?:\\.\\d+)?)\\s*([MK])bps", Pattern.CASE_INSENSITIVE);

    private SpecText() {
    }

    /** @param text 화면 표기 ("데이터 11GB + 매일 2GB"), gb 기본 제공량 (무제한 · 추출 실패면 null) */
    public record Data(String text, BigDecimal gb, String qos) {
    }

    public static Data data(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Data(null, null, null);
        }
        String s = raw.replace("월 데이터", "").replace("데이터", "").replaceAll("\\s+", " ").trim();
        String[] tokens = s.split("\\+");
        String amountText = null;
        BigDecimal gb = null;
        String daily = null;
        String qos = null;
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            Matcher speed = SPEED.matcher(t);
            Matcher dailyM = DAILY.matcher(t);
            if (speed.find() && !AMOUNT.matcher(t).find()) {
                qos = speed(speed);
            } else if (dailyM.find()) {
                daily = "매일 " + amount(dailyM.group(1), dailyM.group(2));
            } else if (i == 0 && t.contains("무제한")) {
                amountText = "무제한";
            } else if (i == 0) {
                Matcher a = AMOUNT.matcher(t);
                if (a.find()) {
                    amountText = amount(a.group(1), a.group(2));
                    gb = toGb(a.group(1), a.group(2));
                }
            }
        }
        if (amountText == null) {
            // 일 데이터만 있는 요금제는 기본 제공량을 운영자가 보완한다
            return new Data(daily != null ? "데이터 " + daily : null, null, qos);
        }
        String text = "데이터 " + amountText + (daily != null ? " + " + daily : "");
        return new Data(text, gb, qos);
    }

    /** 문구 끝의 소진 후 속도 ("…7GB + 1Mbps", "소진 후 최대 3Mbps"). 없으면 null. */
    public static String qosIn(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = SPEED_AFTER_QUOTA.matcher(text);
        if (m.find()) {
            return speed(m);
        }
        Matcher plus = Pattern.compile("\\+\\s*(\\d+(?:\\.\\d+)?)\\s*([MK])bps", Pattern.CASE_INSENSITIVE).matcher(text);
        String last = null;
        while (plus.find()) {
            last = speed(plus);
        }
        return last;
    }

    /** "음성통화 100 분" → "100분", "기본제공" → "기본 제공". */
    public static String voice(String raw) {
        return allowance(raw, "음성통화", "통화", "음성");
    }

    public static String sms(String raw) {
        return allowance(raw, "문자");
    }

    public static Integer won(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = WON.matcher(raw);
        return m.find() ? Integer.valueOf(m.group(1).replace(",", "")) : null;
    }

    /** @param months 할인 기간 (개월), price 할인 후 요금 */
    public record Discount(int months, int price) {
    }

    /** 할인 문구 해석. "N개월 차부터" 는 N-1개월 할인. 할인 후 요금이 현재 요금과 같으면 할인 아님. */
    public static Discount discount(String raw, Integer currentPrice) {
        if (raw == null) {
            return null;
        }
        Discount d = null;
        Matcher after = AFTER.matcher(raw);
        Matcher from = FROM.matcher(raw);
        if (after.find()) {
            d = new Discount(Integer.parseInt(after.group(1)), Integer.parseInt(after.group(2).replace(",", "")));
        } else if (from.find()) {
            d = new Discount(Integer.parseInt(from.group(1)) - 1, Integer.parseInt(from.group(2).replace(",", "")));
        }
        if (d == null || d.months() <= 0 || (currentPrice != null && d.price() == currentPrice)) {
            return null;
        }
        return d;
    }

    private static String allowance(String raw, String... prefixes) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length()).trim();
                break;
            }
        }
        if (s.isEmpty()) {
            return null;
        }
        if (s.replace(" ", "").equals("기본제공")) {
            return "기본 제공";
        }
        Matcher m = COUNT.matcher(s);
        if (m.matches()) {
            return m.group(1) + m.group(2);
        }
        return s;
    }

    private static String amount(String number, String unit) {
        String u = unit.toUpperCase();
        return number + (u.startsWith("G") ? "GB" : "MB");
    }

    private static BigDecimal toGb(String number, String unit) {
        BigDecimal n = new BigDecimal(number);
        if (unit.toUpperCase().startsWith("M")) {
            n = n.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
        }
        return n.stripTrailingZeros();
    }

    private static String speed(Matcher m) {
        return m.group(1) + m.group(2).toUpperCase() + "bps";
    }
}
