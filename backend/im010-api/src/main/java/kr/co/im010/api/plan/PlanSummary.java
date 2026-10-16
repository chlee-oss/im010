package kr.co.im010.api.plan;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import kr.co.im010.core.row.PlanRow;

/**
 * 요금제 카드 · 목록 · 계산기 결과에 쓰는 응답.
 * voice · sms · discountMonths · priceAfterDiscount 는 제휴사별 요금제 카드용, mainPick 은 메인 노출 순서 (1 · 2, 없으면 null).
 */
public record PlanSummary(
        long id,
        String partnerCode,
        String partnerName,
        String planType,
        String name,
        String dataText,
        boolean unlimited,
        BigDecimal dataGb,
        String network,
        String networkLabel,
        String generation,
        Integer monthlyPrice,
        Integer chargePrice,
        Integer validDays,
        String qos,
        List<String> tags,
        String voice,
        String sms,
        Integer discountMonths,
        Integer priceAfterDiscount,
        Integer mainPick
) {

    public static PlanSummary from(PlanRow r) {
        return new PlanSummary(
                r.id(), r.partnerCode(), r.partnerName(), r.planType(), r.name(), r.dataText(),
                r.dataGb() == null, r.dataGb(),
                r.network(), Network.labelOf(r.network()), r.generation(),
                r.monthlyPrice(), r.chargePrice(), r.validDays(), r.qosText(),
                splitPipe(r.tags()), r.voiceText(), r.smsText(), r.discountMonths(), r.priceAfterDiscount(), r.mainPick());
    }

    static List<String> splitPipe(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("\\|")).filter(s -> !s.isBlank()).toList();
    }
}
