package kr.co.im010.api.internet;

import java.util.Arrays;
import java.util.List;

import kr.co.im010.api.plan.Network;
import kr.co.im010.core.row.InternetProductRow;

/**
 * @param applyPath 상담 신청 이동 주소 (제휴업체 신청 페이지로 포워딩)
 */
public record InternetProduct(
        long id,
        String carrier,
        String carrierLabel,
        String productType,
        String name,
        int monthlyPrice,
        List<String> benefits,
        String applyPath
) {

    public static InternetProduct from(InternetProductRow r) {
        List<String> benefits = r.benefits() == null || r.benefits().isBlank()
                ? List.of()
                : Arrays.stream(r.benefits().split("\\|")).filter(s -> !s.isBlank()).toList();
        String label = "LGU".equals(r.carrier()) ? "LG U+" : Network.labelOf(r.carrier());
        return new InternetProduct(r.id(), r.carrier(), label, r.productType(), r.name(), r.monthlyPrice(),
                benefits, "/go/internet/" + r.id() + "?from=MAIN");
    }
}
