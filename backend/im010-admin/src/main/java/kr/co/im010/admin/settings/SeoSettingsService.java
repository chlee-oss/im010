package kr.co.im010.admin.settings;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.core.content.SiteMeta;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.SettingRow;

/**
 * ST-08 검색엔진설정: 메인 · 기본 틀의 사이트 제목 · 설명 · Open Graph 제목 · 설명.
 * 저장하면 im010-api 가 1분 안에 메인 HTML 에 반영하고, 미리 렌더링(매일 05:00 · 배포 빌드)도 같은 값을 쓴다.
 */
@Service
public class SeoSettingsService {

    public static final String PROGRAM = "ST-08";

    /** 화면 · 저장 요청. ogTitle · ogDescription 은 비워 두면 사이트 제목 · 설명을 쓴다 */
    public record SeoRequest(String title, String description, String ogTitle, String ogDescription) {
    }

    public record Limits(int titleMax, int descriptionMax) {
    }

    public record SeoView(String title, String description, String ogTitle, String ogDescription,
                          String defaultTitle, String defaultDescription, Limits limits,
                          String updatedBy, OffsetDateTime updatedAt) {
    }

    private final SettingsMapper settings;
    private final AuditService audit;

    public SeoSettingsService(SettingsMapper settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    public SeoView get() {
        SiteMeta m = SiteMeta.load(settings);
        SettingRow last = settings.findSettings(SiteMeta.KEYS).stream()
                .filter(r -> r.updatedBy() != null)
                .max(Comparator.comparing(SettingRow::updatedAt))
                .orElse(null);
        return new SeoView(m.title(), m.description(), m.ogTitle(), m.ogDescription(),
                SiteMeta.DEFAULT_TITLE, SiteMeta.DEFAULT_DESCRIPTION,
                new Limits(SiteMeta.TITLE_MAX, SiteMeta.DESCRIPTION_MAX),
                last == null ? null : last.updatedBy(), last == null ? null : last.updatedAt());
    }

    @Transactional
    public SeoView save(SeoRequest r) {
        SiteMeta next = validate(r);
        SiteMeta prev = SiteMeta.load(settings);
        String by = CurrentAdmin.get().loginId();

        List<String> changes = new ArrayList<>();
        change(changes, SiteMeta.TITLE, "사이트 제목", prev.title(), next.title(), by);
        change(changes, SiteMeta.DESCRIPTION, "사이트 설명", prev.description(), next.description(), by);
        change(changes, SiteMeta.OG_TITLE, "OG 제목", prev.ogTitle(), next.ogTitle(), by);
        change(changes, SiteMeta.OG_DESCRIPTION, "OG 설명", prev.ogDescription(), next.ogDescription(), by);
        if (!changes.isEmpty()) {
            audit.action(PROGRAM, "SEO_UPDATE", "검색엔진설정", String.join("\n", changes));
        }
        return get();
    }

    /** 공백 정리 · 필수 · 최대 글자 수 · 꺾쇠(<, >) 금지 */
    static SiteMeta validate(SeoRequest r) {
        String title = required(r.title(), "사이트 제목", SiteMeta.TITLE_MAX);
        String description = required(r.description(), "사이트 설명", SiteMeta.DESCRIPTION_MAX);
        String ogTitle = optional(r.ogTitle(), "Open Graph 제목", SiteMeta.TITLE_MAX);
        String ogDescription = optional(r.ogDescription(), "Open Graph 설명", SiteMeta.DESCRIPTION_MAX);
        return new SiteMeta(title, description, ogTitle, ogDescription);
    }

    private static String required(String value, String label, int max) {
        String v = optional(value, label, max);
        if (v.isEmpty()) {
            throw ApiException.badRequest(label + "을(를) 입력해 주세요");
        }
        return v;
    }

    private static String optional(String value, String label, int max) {
        String v = SiteMeta.clean(value);
        if (SiteMeta.length(v) > max) {
            throw ApiException.badRequest(label + "은(는) " + max + "자 이내로 입력해 주세요 (지금 " + SiteMeta.length(v) + "자)");
        }
        if (v.contains("<") || v.contains(">")) {
            throw ApiException.badRequest(label + "에는 < > 를 쓸 수 없습니다");
        }
        return v;
    }

    private void change(List<String> changes, String key, String label, String before, String after, String by) {
        if (Objects.equals(before, after)) {
            return;
        }
        settings.updateSetting(key, after, by);
        changes.add(label + ": \"" + before + "\" → \"" + after + "\"");
    }
}
