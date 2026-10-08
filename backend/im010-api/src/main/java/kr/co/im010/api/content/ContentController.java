package kr.co.im010.api.content;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.api.web.NotFoundException;
import kr.co.im010.core.mapper.ContentMapper;
import kr.co.im010.core.row.FaqRow;
import kr.co.im010.core.row.FooterRow;
import kr.co.im010.core.row.TermsRow;

/** 프런트 고지 콘텐츠 (백오피스 ST-04 약관 · ST-06 Footer · ST-07 FAQ 에서 관리). */
@RestController
@RequestMapping("/api")
public class ContentController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> TYPES = Set.of("SERVICE", "PRIVACY", "COLLECT", "THIRD_PARTY", "MARKETING");
    private static final CacheControl SHORT = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    /** 시행 중이거나 시행 예정인 약관 버전 (본문 제외) */
    public record TermsVersion(long id, String version, LocalDate effectiveOn, boolean current) {
    }

    public record Terms(String type, String version, LocalDate effectiveOn, String body, List<TermsVersion> versions) {
    }

    public record Footer(String companyName, String ceo, String businessNo, String mailOrderNo, String address,
                         String csPhone, String csHours, String email, String notice) {
    }

    public record Faq(long id, String category, String question, String answer) {
    }

    private final ContentMapper contentMapper;

    public ContentController(ContentMapper contentMapper) {
        this.contentMapper = contentMapper;
    }

    /** 약관: 기본은 시행 중인 버전, id 를 주면 게시된 다른 버전 (이전 · 시행 예정 버전 보기) */
    @GetMapping("/terms/{type}")
    public ResponseEntity<Terms> terms(@PathVariable String type, @RequestParam(required = false) Long id) {
        if (!TYPES.contains(type)) {
            throw new NotFoundException("terms " + type);
        }
        LocalDate today = LocalDate.now(KST);
        TermsRow current = contentMapper.findCurrentTerm(type, today);
        List<TermsRow> published = contentMapper.findPublishedTerms(type);
        TermsRow shown = id == null ? current
                : published.stream().filter(t -> t.id() == id).findFirst().orElseThrow(() -> new NotFoundException("terms " + id));
        if (shown == null) {
            throw new NotFoundException("terms " + type);
        }
        List<TermsVersion> versions = published.stream()
                .map(t -> new TermsVersion(t.id(), t.version(), t.effectiveOn(), current != null && t.id() == current.id()))
                .toList();
        return ResponseEntity.ok().cacheControl(SHORT)
                .body(new Terms(type, shown.version(), shown.effectiveOn(), shown.body(), versions));
    }

    @GetMapping("/footer")
    public ResponseEntity<Footer> footer() {
        FooterRow f = contentMapper.findLatestFooter();
        if (f == null) {
            throw new NotFoundException("footer");
        }
        return ResponseEntity.ok().cacheControl(SHORT).body(new Footer(f.companyName(), f.ceo(), f.businessNo(),
                f.mailOrderNo(), f.address(), f.csPhone(), f.csHours(), f.email(), f.notice()));
    }

    @GetMapping("/faqs")
    public ResponseEntity<List<Faq>> faqs() {
        return ResponseEntity.ok().cacheControl(SHORT).body(contentMapper.findFaqs(true).stream()
                .map((FaqRow f) -> new Faq(f.id(), f.category(), f.question(), f.answer())).toList());
    }
}
