package kr.co.im010.core.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.SettingRow;

/**
 * 검색엔진설정 (ST-08) 페이지별 제목 · 설명 (admin_setting seo_page_{key}_title · _description).
 * 비어 있으면 자동 문구 (미리 렌더링 prerender.mjs 가 게시 중인 요금제 수 등으로 만든다).
 * 설명에는 {개수} · {제휴사수} 를 쓸 수 있다 (미리 렌더링 때 게시 중인 수로 바뀐다).
 * 쓰는 곳: 백오피스 저장 · 검증, /api/site-meta (pages), 미리 렌더링, 고객 화면 탭 제목
 *
 * @param key 페이지 키 (weekly · find · postpaid · prepaid · internet · ai · faq)
 */
public record PageMeta(String key, String title, String description) {

    /** 페이지 키 → 주소 · 이름 · 자동 제목 · 자동 설명(안내용). 순서 = 화면 순서 */
    public record Page(String key, String path, String name, String defaultTitle, String defaultDescription) {
    }

    public static final List<Page> PAGES = List.of(
            new Page("weekly", "/weekly", "금주의 요금, 이렇게 추천해요", "im010 - 금주의 요금, 이렇게 추천해요",
                    "영상 · 데이터 · 통화 · 가격 기준별로 이번 주 제휴사 {제휴사수}곳 요금제를 하나씩 골라 추천해요."),
            new Page("find", "/find", "내가 찾는 요금제를 직접 찾아요", "im010 - 내가 찾는 요금제를 직접 찾아요",
                    "데이터 · 통신망을 골라 최저가를 찾고, 무제한 · 1만원 이하 등 조건별 요금제를 바로 확인하세요."),
            new Page("postpaid", "/postpaid", "입점한 제휴사에서 소개해요", "im010 - 입점한 제휴사에서 소개해요",
                    "{제휴사수}개 제휴사 후불 알뜰폰 요금제 {개수}개를 월 요금 · 데이터 · 통신망별로 비교해 보세요."),
            new Page("prepaid", "/prepaid", "선불요금제를 알려 드릴게요", "im010 - 선불요금제를 알려 드릴게요",
                    "{제휴사수}개 제휴사 선불 알뜰폰 요금제 {개수}개를 충전 금액 · 사용 기간 · 데이터별로 비교해 보세요."),
            new Page("internet", "/internet", "인터넷도 im010에서 한 번에", "im010 - 인터넷도 im010에서 한 번에",
                    "인터넷 단독 · 인터넷 + TV 결합 상품 {개수}개를 비교해 보세요. 통신사별 월 요금과 혜택을 한눈에."),
            new Page("ai", "/ai", "알뜰AI", "im010 - 나는 알뜰한 통신경험을 원해!",
                    "알뜰폰 · 인터넷 요금과 혜택 소식을 1분 안에 읽을 수 있게 정리했어요. 최신 글: (가장 최근 글 제목)"),
            new Page("faq", "/faq", "자주 묻는 질문", "자주 묻는 질문 | im010",
                    "알뜰폰 개통 · 요금제 · 선불폰 이용에 대해 자주 묻는 질문과 답변을 모았어요."));

    public PageMeta {
        title = SiteMeta.clean(title);
        description = SiteMeta.clean(description);
    }

    public static String titleKey(String page) {
        return "seo_page_" + page + "_title";
    }

    public static String descriptionKey(String page) {
        return "seo_page_" + page + "_description";
    }

    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Page p : PAGES) {
            out.add(titleKey(p.key()));
            out.add(descriptionKey(p.key()));
        }
        return out;
    }

    public static boolean known(String key) {
        return PAGES.stream().anyMatch(p -> p.key().equals(key));
    }

    /** 저장된 값 (없으면 빈 문자열 = 자동). 화면 순서대로 */
    public static Map<String, PageMeta> load(SettingsMapper mapper) {
        Map<String, String> v = new HashMap<>();
        for (SettingRow r : mapper.findSettings(keys())) {
            v.put(r.key(), r.value());
        }
        Map<String, PageMeta> out = new LinkedHashMap<>();
        for (Page p : PAGES) {
            out.put(p.key(), new PageMeta(p.key(), v.get(titleKey(p.key())), v.get(descriptionKey(p.key()))));
        }
        return out;
    }
}
