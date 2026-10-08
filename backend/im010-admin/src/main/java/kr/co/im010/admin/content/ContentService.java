package kr.co.im010.admin.content;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.ContentMapper;
import kr.co.im010.core.row.FaqRow;
import kr.co.im010.core.row.FooterRow;
import kr.co.im010.core.row.TermsRow;

/**
 * 프런트 고지 콘텐츠: ST-04 약관관리 · ST-06 Footer관리 · ST-07 1:1문의관리 [FAQ].
 * 약관은 게시하면 고치지 않고 새 버전을 만든다 (프런트에서 이전 버전을 볼 수 있어야 하므로).
 */
@Service
public class ContentService {

    static final String TERMS = "ST-04";
    static final String FOOTER = "ST-06";
    static final String FAQ = "ST-07";
    static final Set<String> TERMS_TYPES = Set.of("SERVICE", "PRIVACY", "COLLECT", "THIRD_PARTY", "MARKETING");
    private static final int BODY_MAX = 200_000;

    public record TermsRequest(String version, String body, LocalDate effectiveOn) {
    }

    public record FooterRequest(String companyName, String ceo, String businessNo, String mailOrderNo, String address,
                                String csPhone, String csHours, String email, String notice) {
    }

    public record FaqRequest(String category, String question, String answer, int sortOrder, boolean exposed) {
    }

    private final ContentMapper contentMapper;
    private final AuditService audit;

    public ContentService(ContentMapper contentMapper, AuditService audit) {
        this.contentMapper = contentMapper;
        this.audit = audit;
    }

    // ---------- 약관 ----------

    public List<TermsRow> terms(String type) {
        return contentMapper.findTerms(type(type));
    }

    public TermsRow term(long id) {
        return findTerm(id);
    }

    @Transactional
    public TermsRow createTerm(String type, TermsRequest req) {
        String t = type(type);
        TermsRequest r = validate(t, req, null);
        long id = contentMapper.insertTerm(t, r.version(), r.body(), r.effectiveOn(), CurrentAdmin.get().loginId());
        audit.action(TERMS, "TERMS_CREATE", t + " " + r.version(), "시행 " + r.effectiveOn());
        return findTerm(id);
    }

    @Transactional
    public TermsRow updateTerm(long id, TermsRequest req) {
        TermsRow term = findTerm(id);
        TermsRequest r = validate(term.termsType(), req, id);
        if (contentMapper.updateDraftTerm(id, r.version(), r.body(), r.effectiveOn()) == 0) {
            throw ApiException.conflict("게시한 약관은 고칠 수 없습니다. 새 버전을 만들어 주세요");
        }
        audit.action(TERMS, "TERMS_UPDATE", term.termsType() + " " + r.version(), null);
        return findTerm(id);
    }

    @Transactional
    public void deleteTerm(long id) {
        TermsRow term = findTerm(id);
        if (contentMapper.deleteDraftTerm(id) == 0) {
            throw ApiException.conflict("게시한 약관은 지울 수 없습니다");
        }
        audit.action(TERMS, "TERMS_DELETE", term.termsType() + " " + term.version(), null);
    }

    @Transactional
    public TermsRow publishTerm(long id) {
        TermsRow term = findTerm(id);
        if (contentMapper.publishTerm(id, CurrentAdmin.get().loginId()) == 0) {
            throw ApiException.conflict("이미 게시한 약관입니다");
        }
        audit.action(TERMS, "TERMS_PUBLISH", term.termsType() + " " + term.version(), "시행 " + term.effectiveOn());
        return findTerm(id);
    }

    // ---------- Footer ----------

    public List<FooterRow> footerHistory() {
        return contentMapper.findFooterHistory(20);
    }

    /** 저장하면 새 버전이 되어 바로 프런트에 나간다 (이전 값은 이력으로 남는다). */
    @Transactional
    public List<FooterRow> saveFooter(FooterRequest r) {
        String email = len(r.email(), 100, "이메일");
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("이메일 형식이 올바르지 않습니다");
        }
        FooterRow row = new FooterRow(0, req(r.companyName(), 100, "상호"), req(r.ceo(), 50, "대표"),
                req(r.businessNo(), 20, "사업자등록번호"), len(r.mailOrderNo(), 50, "통신판매업신고"),
                req(r.address(), 200, "주소"), req(r.csPhone(), 30, "고객센터"), len(r.csHours(), 100, "운영 시간"),
                email, len(r.notice(), 2000, "고지 문구"), CurrentAdmin.get().loginId(), null);
        contentMapper.insertFooter(row);
        audit.action(FOOTER, "FOOTER_SAVE", row.companyName(), null);
        return footerHistory();
    }

    // ---------- FAQ ----------

    public List<FaqRow> faqs() {
        return contentMapper.findFaqs(false);
    }

    @Transactional
    public FaqRow createFaq(FaqRequest r) {
        long id = contentMapper.insertFaq(req(r.category(), 30, "분류"), req(r.question(), 200, "질문"),
                req(r.answer(), 5000, "답변"), r.sortOrder(), r.exposed(), CurrentAdmin.get().loginId());
        audit.action(FAQ, "FAQ_CREATE", "faq " + id, r.question());
        return contentMapper.findFaq(id);
    }

    @Transactional
    public FaqRow updateFaq(long id, FaqRequest r) {
        if (contentMapper.updateFaq(id, req(r.category(), 30, "분류"), req(r.question(), 200, "질문"),
                req(r.answer(), 5000, "답변"), r.sortOrder(), r.exposed(), CurrentAdmin.get().loginId()) == 0) {
            throw ApiException.notFound("FAQ");
        }
        audit.action(FAQ, "FAQ_UPDATE", "faq " + id, r.exposed() ? null : "비노출");
        return contentMapper.findFaq(id);
    }

    @Transactional
    public void deleteFaq(long id) {
        FaqRow faq = contentMapper.findFaq(id);
        if (faq == null || contentMapper.deleteFaq(id) == 0) {
            throw ApiException.notFound("FAQ");
        }
        audit.action(FAQ, "FAQ_DELETE", "faq " + id, faq.question());
    }

    private TermsRequest validate(String type, TermsRequest r, Long excludeId) {
        String version = req(r.version(), 20, "버전");
        String body = r.body() == null ? null : r.body().strip();
        if (body == null || body.isEmpty() || body.length() > BODY_MAX) {
            throw ApiException.badRequest("본문을 입력해 주세요 (" + BODY_MAX + "자 이하)");
        }
        if (r.effectiveOn() == null) {
            throw ApiException.badRequest("시행일을 입력해 주세요");
        }
        if (contentMapper.existsTermsVersion(type, version, excludeId)) {
            throw ApiException.conflict("이미 있는 버전입니다");
        }
        return new TermsRequest(version, body, r.effectiveOn());
    }

    private TermsRow findTerm(long id) {
        TermsRow t = contentMapper.findTerm(id);
        if (t == null) {
            throw ApiException.notFound("약관");
        }
        return t;
    }

    private static String type(String type) {
        if (type == null || !TERMS_TYPES.contains(type)) {
            throw ApiException.badRequest("약관 종류가 올바르지 않습니다");
        }
        return type;
    }

    private static String req(String s, int max, String label) {
        String t = len(s, max, label);
        if (t == null) {
            throw ApiException.badRequest(label + "을(를) 입력해 주세요");
        }
        return t;
    }

    private static String len(String s, int max, String label) {
        String t = Texts.trim(s);
        if (t != null && t.length() > max) {
            throw ApiException.badRequest(label + "은(는) " + max + "자 이하여야 합니다");
        }
        return t;
    }
}
