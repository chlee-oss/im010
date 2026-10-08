package kr.co.im010.core.row;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 새 요금제 버전 저장용. tags · supplementedFields · changedFields 는 배열. */
public record NewPlanVersion(
        long planId,
        int versionNo,
        String name,
        String dataText,
        BigDecimal dataGb,
        String qosText,
        String voiceText,
        String smsText,
        String network,
        String generation,
        Integer monthlyPrice,
        Integer chargePrice,
        Integer validDays,
        Integer discountMonths,
        Integer priceAfterDiscount,
        String[] tags,
        String[] supplementedFields,
        String[] changedFields,
        LocalDate collectedOn,
        String reviewer,
        String approver,
        Long crawlItemId,
        String sourceHash
) {
}
