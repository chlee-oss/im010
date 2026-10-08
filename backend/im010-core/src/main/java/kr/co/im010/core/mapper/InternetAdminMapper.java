package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.InternetPartnerRow;
import kr.co.im010.core.row.InternetProductAdminRow;

/** 인터넷 제휴업체 (PA-01 [인터넷]) · 인터넷 상품 (PR-02). */
@Mapper
public interface InternetAdminMapper {

    List<InternetPartnerRow> findPartners();

    InternetPartnerRow findPartner(@Param("id") long id);

    long insertPartner(@Param("p") InternetPartnerRow partner);

    void updatePartner(@Param("p") InternetPartnerRow partner);

    List<InternetProductAdminRow> findProducts(@Param("productType") String productType);

    InternetProductAdminRow findProduct(@Param("id") long id);

    long insertProduct(@Param("carrier") String carrier, @Param("productType") String productType, @Param("name") String name,
                       @Param("monthlyPrice") int monthlyPrice, @Param("benefits") String[] benefits,
                       @Param("partnerId") Long partnerId, @Param("applyUrl") String applyUrl,
                       @Param("sortOrder") int sortOrder, @Param("exposed") boolean exposed);

    void updateProduct(@Param("id") long id, @Param("carrier") String carrier, @Param("productType") String productType,
                       @Param("name") String name, @Param("monthlyPrice") int monthlyPrice,
                       @Param("benefits") String[] benefits, @Param("partnerId") Long partnerId,
                       @Param("applyUrl") String applyUrl, @Param("sortOrder") int sortOrder,
                       @Param("exposed") boolean exposed);

    /** 계약이 끝난 업체에 연결된 노출 상품 수 (상품 업체 교체 안내) */
    int countExposedProductsOfEndedPartner(@Param("partnerId") long partnerId);

    /** 오늘 기준 계약 종료일이 지난 업체를 종료로 (배치 · 화면 조회 때 맞춘다) */
    int endExpiredPartners(@Param("today") LocalDate today);
}
