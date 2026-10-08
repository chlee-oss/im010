package kr.co.im010.admin.plan;

import java.util.ArrayList;
import java.util.List;

import kr.co.im010.core.row.PlanVersionRow;

/**
 * 게시 예약 · 즉시 게시 전 필수 항목 (백오피스 3.2). 하나라도 비어 있으면 예약 · 게시할 수 없다.
 */
public final class PublishRules {

    private PublishRules() {
    }

    /** 비어 있는 항목 이름 (화면 표기). 없으면 빈 목록. */
    public static List<String> missing(String planType, PlanVersionRow v, String activationUrl) {
        List<String> m = new ArrayList<>();
        if (v == null) {
            m.add("게시할 버전");
            return m;
        }
        if (blank(v.name())) {
            m.add("요금제명");
        }
        if (blank(v.dataText())) {
            m.add("데이터");
        } else if (v.dataGb() == null && !v.dataText().contains("무제한")) {
            m.add("데이터 기본 제공량");
        }
        if (blank(v.network())) {
            m.add("망");
        }
        if (blank(v.generation())) {
            m.add("세대");
        }
        if ("PREPAID".equals(planType)) {
            if (v.chargePrice() == null) {
                m.add("충전 금액");
            }
            if (v.validDays() == null) {
                m.add("사용 기간");
            }
        } else {
            if (v.monthlyPrice() == null) {
                m.add("월 요금");
            }
            if ((v.discountMonths() == null) != (v.priceAfterDiscount() == null)) {
                m.add("할인 기간 · 할인 후 요금");
            }
        }
        if (blank(activationUrl)) {
            m.add("개통 URL");
        }
        return m;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
