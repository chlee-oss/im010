package kr.co.im010.core.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 제휴사 URL 정리: 세션 ID(;jsessionid=...)와 빈 파라미터를 빼서 같은 페이지가 같은 문자열이 되게 한다. */
public final class Urls {

    private static final Pattern JSESSIONID = Pattern.compile(";jsessionid=[^?#]*", Pattern.CASE_INSENSITIVE);

    private Urls() {
    }

    public static String clean(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = JSESSIONID.matcher(url.trim()).replaceAll("");
        int hash = u.indexOf('#');
        if (hash >= 0) {
            u = u.substring(0, hash);
        }
        int q = u.indexOf('?');
        if (q < 0) {
            return u;
        }
        List<String> kept = new ArrayList<>();
        for (String param : u.substring(q + 1).split("&")) {
            int eq = param.indexOf('=');
            if (!param.isEmpty() && eq != param.length() - 1) {
                kept.add(param);
            }
        }
        return kept.isEmpty() ? u.substring(0, q) : u.substring(0, q) + "?" + String.join("&", kept);
    }

    /** 쿼리 파라미터 값. 없으면 null. */
    public static String param(String url, String name) {
        if (url == null) {
            return null;
        }
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String param : url.substring(q + 1).split("&")) {
            if (param.startsWith(name + "=")) {
                String v = param.substring(name.length() + 1);
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }
}
