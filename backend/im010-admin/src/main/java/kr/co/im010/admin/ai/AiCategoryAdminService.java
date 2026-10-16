package kr.co.im010.admin.ai;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.AiMapper;
import kr.co.im010.core.row.AiCategoryRow;

/**
 * 알뜰AI 카테고리관리 (CT-02): 이름 · 주소 · 카드 색 · 순서 · 노출. 글이 있는 카테고리는 지우지 않고 비노출.
 */
@Service
public class AiCategoryAdminService {

    public static final String PROGRAM = "CT-02";
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,19}$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public record CategoryRequest(String code, String name, String slug, String bgColor, String accentColor, int sortOrder,
                                  boolean exposed) {
    }

    private final AiMapper aiMapper;
    private final AuditService audit;

    public AiCategoryAdminService(AiMapper aiMapper, AuditService audit) {
        this.aiMapper = aiMapper;
        this.audit = audit;
    }

    public List<AiCategoryRow> list() {
        return aiMapper.findCategories(false);
    }

    @Transactional
    public List<AiCategoryRow> create(CategoryRequest r) {
        String code = r.code() == null ? null : r.code().trim().toUpperCase();
        if (code == null || !CODE.matcher(code).matches()) {
            throw ApiException.badRequest("코드는 영문 대문자로 시작하는 영문 대문자 · 숫자 · _ 2 ~ 20자여야 합니다");
        }
        if (aiMapper.findCategory(code) != null) {
            throw ApiException.conflict("이미 있는 코드입니다");
        }
        CategoryRequest v = validate(r, null);
        aiMapper.insertCategory(code, v.name(), v.slug(), v.bgColor(), v.accentColor(), v.sortOrder(), v.exposed());
        audit.action(PROGRAM, "AI_CATEGORY_CREATE", code, v.name());
        return list();
    }

    @Transactional
    public List<AiCategoryRow> update(String code, CategoryRequest r) {
        if (aiMapper.findCategory(code) == null) {
            throw ApiException.notFound("카테고리");
        }
        CategoryRequest v = validate(r, code);
        aiMapper.updateCategory(code, v.name(), v.slug(), v.bgColor(), v.accentColor(), v.sortOrder(), v.exposed());
        audit.action(PROGRAM, "AI_CATEGORY_UPDATE", code, v.name() + (v.exposed() ? "" : " (비노출)"));
        return list();
    }

    private CategoryRequest validate(CategoryRequest r, String code) {
        String name = Texts.trim(r.name());
        if (name == null || name.length() > 30) {
            throw ApiException.badRequest("이름은 1 ~ 30자여야 합니다");
        }
        String slug = Texts.trim(r.slug());
        if (slug == null || slug.length() > 40 || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest("주소는 영문 소문자 · 숫자와 '-' 만 쓸 수 있습니다 (예: mobile)");
        }
        if (slug.equals("posts") || slug.equals("all")) {
            throw ApiException.badRequest("posts · all 은 주소로 쓸 수 없습니다");
        }
        if (aiMapper.existsCategorySlug(slug, code)) {
            throw ApiException.conflict("이미 쓰고 있는 주소입니다");
        }
        if (r.bgColor() == null || !COLOR.matcher(r.bgColor()).matches() || r.accentColor() == null
                || !COLOR.matcher(r.accentColor()).matches()) {
            throw ApiException.badRequest("색은 #RRGGBB 형식이어야 합니다");
        }
        return new CategoryRequest(code, name, slug, r.bgColor().toUpperCase(), r.accentColor().toUpperCase(), r.sortOrder(),
                r.exposed());
    }
}
