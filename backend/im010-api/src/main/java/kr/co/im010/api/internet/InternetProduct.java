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
        return new InternetProduct(r.id(), r.carrier(), carrierLabel(r.carrier()), r.productType(), r.name(), r.monthlyPrice(),
                benefits, "/go/internet/" + r.id() + "?from=MAIN");
    }

    /** 인터넷 통신사 표기: LGU → LG U+, SKB → SKB(알뜰, 휴대폰 통신망에는 없는 인터넷 전용 코드) */
    static String carrierLabel(String carrier) {
        return switch (carrier) {
            case "LGU" -> "LG U+";
            case "SKB" -> "SKB(알뜰)";
            default -> Network.labelOf(carrier);
        };
    }
}
