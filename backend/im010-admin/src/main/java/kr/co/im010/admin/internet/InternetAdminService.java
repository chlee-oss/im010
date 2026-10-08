package kr.co.im010.admin.internet;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.InternetAdminMapper;
import kr.co.im010.core.row.InternetPartnerRow;
import kr.co.im010.core.row.InternetProductAdminRow;

/**
 * 인터넷 제휴업체 (PA-01 [인터넷]) · 인터넷 상품 (PR-02). 인터넷 신청은 im010이 받지 않고 업체 신청 페이지로 포워딩한다 (결정 #18).
 * 상품의 신청 URL이 비어 있으면 업체 기본 URL로 보낸다. 계약 종료일이 지난 업체는 조회할 때 종료로 바꾼다.
 */
@Service
public class InternetAdminService {

    static final String PARTNERS = "PA-01";
    static final String PRODUCTS = "PR-02";
    private static final Set<String> CARRIERS = Set.of("SKT", "KT", "LGU");
    private static final Set<String> TYPES = Set.of("SINGLE", "BUNDLE");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_BENEFITS = 5;

    public record PartnerRequest(String name, String carrier, String applyUrl, String status, String businessNo,
                                 String contactName, String contactPhone, LocalDate contractStart, LocalDate contractEnd,
                                 String memo) {
    }

    public record ProductRequest(String carrier, String productType, String name, Integer monthlyPrice,
                                 List<String> benefits, Long internetPartnerId, String applyUrl, int sortOrder,
                                 boolean exposed) {
    }

    /** warning: 상품이 연결된 업체를 종료로 바꿨을 때 교체 안내 */
    public record PartnerResult(InternetPartnerRow partner, String warning) {
    }

    private final InternetAdminMapper mapper;
    private final AuditService audit;

    public InternetAdminService(InternetAdminMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public List<InternetPartnerRow> partners() {
        mapper.endExpiredPartners(LocalDate.now(KST));
        return mapper.findPartners();
    }

    @Transactional
    public PartnerResult createPartner(PartnerRequest req) {
        InternetPartnerRow p = validate(0, req);
        long id = mapper.insertPartner(p);
        audit.action(PARTNERS, "INTERNET_PARTNER_CREATE", p.name(), p.carrier());
        return new PartnerResult(mapper.findPartner(id), null);
    }

    @Transactional
    public PartnerResult updatePartner(long id, PartnerRequest req) {
        InternetPartnerRow before = mapper.findPartner(id);
        if (before == null) {
            throw ApiException.notFound("제휴업체");
        }
        InternetPartnerRow p = validate(id, req);
        mapper.updatePartner(p);
        audit.action(PARTNERS, "INTERNET_PARTNER_UPDATE", p.name(), p.status());
        int exposed = mapper.countExposedProductsOfEndedPartner(id);
        String warning = exposed > 0 ? "이 업체에 연결된 노출 상품 " + exposed + "개는 신청 버튼이 동작하지 않습니다. 인터넷관리에서 다른 업체로 바꿔 주세요" : null;
        return new PartnerResult(mapper.findPartner(id), warning);
    }

    public List<InternetProductAdminRow> products(String productType) {
        return mapper.findProducts(TYPES.contains(productType) ? productType : null);
    }

    @Transactional
    public InternetProductAdminRow createProduct(ProductRequest req) {
        ProductRequest r = validate(req);
        long id = mapper.insertProduct(r.carrier(), r.productType(), r.name(), r.monthlyPrice(),
                r.benefits().toArray(String[]::new), r.internetPartnerId(), r.applyUrl(), r.sortOrder(), r.exposed());
        audit.action(PRODUCTS, "INTERNET_PRODUCT_CREATE", r.name(), null);
        return mapper.findProduct(id);
    }

    @Transactional
    public InternetProductAdminRow updateProduct(long id, ProductRequest req) {
        if (mapper.findProduct(id) == null) {
            throw ApiException.notFound("인터넷 상품");
        }
        ProductRequest r = validate(req);
        mapper.updateProduct(id, r.carrier(), r.productType(), r.name(), r.monthlyPrice(),
                r.benefits().toArray(String[]::new), r.internetPartnerId(), r.applyUrl(), r.sortOrder(), r.exposed());
        audit.action(PRODUCTS, "INTERNET_PRODUCT_UPDATE", r.name(), r.exposed() ? null : "비노출");
        return mapper.findProduct(id);
    }

    private InternetPartnerRow validate(long id, PartnerRequest r) {
        String name = Texts.trim(r.name());
        if (name == null || name.length() > 100) {
            throw ApiException.badRequest("업체명은 1 ~ 100자여야 합니다");
        }
        if (r.carrier() == null || !CARRIERS.contains(r.carrier())) {
            throw ApiException.badRequest("통신사를 골라 주세요");
        }
        String url = Texts.trim(r.applyUrl()) == null ? null : Texts.requireHttpsUrl(r.applyUrl(), "신청 페이지 URL");
        if (r.contractStart() != null && r.contractEnd() != null && r.contractEnd().isBefore(r.contractStart())) {
            throw ApiException.badRequest("계약 종료일이 시작일보다 앞섭니다");
        }
        String status = "ENDED".equals(r.status()) ? "ENDED" : "ACTIVE";
        return new InternetPartnerRow(id, name, r.carrier(), url, status, len(r.businessNo(), 20), len(r.contactName(), 50),
                len(r.contactPhone(), 30), r.contractStart(), r.contractEnd(), len(r.memo(), 500), 0);
    }

    private ProductRequest validate(ProductRequest r) {
        if (r.carrier() == null || !CARRIERS.contains(r.carrier())) {
            throw ApiException.badRequest("통신사를 골라 주세요");
        }
        if (r.productType() == null || !TYPES.contains(r.productType())) {
            throw ApiException.badRequest("유형(단독 · 결합)을 골라 주세요");
        }
        String name = Texts.trim(r.name());
        if (name == null || name.length() > 100) {
            throw ApiException.badRequest("상품명은 1 ~ 100자여야 합니다");
        }
        if (r.monthlyPrice() == null || r.monthlyPrice() < 0) {
            throw ApiException.badRequest("월 요금을 입력해 주세요");
        }
        List<String> benefits = r.benefits() == null ? List.of()
                : r.benefits().stream().map(Texts::trim).filter(Objects::nonNull).toList();
        if (benefits.size() > MAX_BENEFITS || benefits.stream().anyMatch(b -> b.length() > 60 || b.contains("|"))) {
            throw ApiException.badRequest("혜택은 " + MAX_BENEFITS + "개까지, 각 60자 이하로 입력해 주세요");
        }
        if (r.internetPartnerId() != null) {
            InternetPartnerRow partner = mapper.findPartner(r.internetPartnerId());
            if (partner == null) {
                throw ApiException.badRequest("없는 제휴업체입니다");
            }
            if (!partner.carrier().equals(r.carrier())) {
                throw ApiException.badRequest("상품과 제휴업체의 통신사가 다릅니다");
            }
        }
        String url = Texts.trim(r.applyUrl()) == null ? null : Texts.requireHttpsUrl(r.applyUrl(), "상품 신청 URL");
        return new ProductRequest(r.carrier(), r.productType(), name, r.monthlyPrice(), benefits, r.internetPartnerId(),
                url, r.sortOrder(), r.exposed());
    }

    private static String len(String s, int max) {
        String t = Texts.trim(s);
        if (t != null && t.length() > max) {
            throw ApiException.badRequest(max + "자 이하로 입력해 주세요");
        }
        return t;
    }
}
