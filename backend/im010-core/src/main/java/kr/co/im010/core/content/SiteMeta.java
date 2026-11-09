package kr.co.im010.core.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.SettingRow;

/**
 * 검색엔진설정 (ST-08): 메인 · 기본 틀의 사이트 제목 · 설명 · Open Graph 제목 · 설명 (admin_setting).
 * OG 제목 · 설명이 비어 있으면 사이트 제목 · 설명을 쓴다. 제목 · 설명이 비어 있으면(설정이 없을 때) 기본값.
 * 쓰는 곳: 백오피스 저장 · 검증, im010-api 메인 HTML 응답 · /api/site-meta, 미리 렌더링(prerender.mjs)
 *
 * @param ogTitle       저장된 값 그대로 (빈 문자열 = 사이트 제목 사용)
 * @param ogDescription 저장된 값 그대로 (빈 문자열 = 사이트 설명 사용)
 */
public record SiteMeta(String title, String description, String ogTitle, String ogDescription) {

    public static final String TITLE = "seo_site_title";
    public static final String DESCRIPTION = "seo_site_description";
    public static final String OG_TITLE = "seo_og_title";
    public static final String OG_DESCRIPTION = "seo_og_description";
    public static final List<String> KEYS = List.of(TITLE, DESCRIPTION, OG_TITLE, OG_DESCRIPTION);

    /** 제목 최대 글자 수 (검색 결과에서 잘리지 않는 길이 — 권장 40자 안팎) */
    public static final int TITLE_MAX = 60;
    /** 설명 최대 글자 수 — 네이버 서치어드바이저 권장 80자 (prerender.mjs DESC_MAX 와 같게) */
    public static final int DESCRIPTION_MAX = 80;

    public static final String DEFAULT_TITLE = "im010 — 알뜰폰 요금비교";
    public static final String DEFAULT_DESCRIPTION = "7개 제휴사 알뜰폰 후불 · 선불 요금제와 인터넷 상품을 한눈에 비교하고 최저가 요금제를 찾아보세요.";

    public SiteMeta {
        title = clean(title);
        description = clean(description);
        ogTitle = clean(ogTitle);
        ogDescription = clean(ogDescription);
        if (title.isEmpty()) {
            title = DEFAULT_TITLE;
        }
        if (description.isEmpty()) {
            description = DEFAULT_DESCRIPTION;
        }
    }

    public static SiteMeta defaults() {
        return new SiteMeta(null, null, null, null);
    }

    public static SiteMeta load(SettingsMapper mapper) {
        Map<String, String> v = new HashMap<>();
        for (SettingRow r : mapper.findSettings(KEYS)) {
            v.put(r.key(), r.value());
        }
        return new SiteMeta(v.get(TITLE), v.get(DESCRIPTION), v.get(OG_TITLE), v.get(OG_DESCRIPTION));
    }

    /** 공유 미리보기에 실제로 쓰는 제목 */
    public String effectiveOgTitle() {
        return ogTitle.isEmpty() ? title : ogTitle;
    }

    /** 공유 미리보기에 실제로 쓰는 설명 */
    public String effectiveOgDescription() {
        return ogDescription.isEmpty() ? description : ogDescription;
    }

    /** 줄바꿈 · 연속 공백을 한 칸으로, 앞뒤 공백 제거. null 은 빈 문자열 */
    public static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }

    /** 글자 수 (한글 · 이모지 모두 1자) */
    public static int length(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }
}
