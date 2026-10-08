package kr.co.im010.admin.web;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** 화면 입력 · DB 문자열 변환 도우미. */
public final class Texts {

    private static final Pattern HTTPS_URL = Pattern.compile("^https://[A-Za-z0-9.-]+(:\\d+)?(/[^\\s]*)?$");

    private Texts() {
    }

    /** DB 에서 '|' 로 이어 받은 배열 → 목록 */
    public static List<String> split(String joined) {
        return joined == null || joined.isBlank() ? List.of() : Arrays.asList(joined.split("\\|"));
    }

    /** 앞뒤 공백 제거, 빈 문자열은 null */
    public static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static boolean isHttpsUrl(String url) {
        return url != null && url.length() <= 500 && HTTPS_URL.matcher(url).matches();
    }

    public static String requireHttpsUrl(String url, String label) {
        String u = trim(url);
        if (!isHttpsUrl(u)) {
            throw ApiException.badRequest(label + "은(는) https:// 로 시작하는 주소여야 합니다");
        }
        return u;
    }
}
