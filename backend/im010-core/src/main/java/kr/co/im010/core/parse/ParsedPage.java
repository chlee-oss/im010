package kr.co.im010.core.parse;

import java.util.List;

/**
 * 요금제 페이지 한 장의 추출 결과.
 *
 * @param plans 페이지 순서대로의 요금제
 * @param tabs  페이지 메뉴에 보이는 카테고리 탭 (미등록 탭 알림용)
 */
public record ParsedPage(List<ParsedPlan> plans, List<Tab> tabs) {

    /** 카테고리 탭. url 은 jsessionid 를 뺀 절대 주소 (…/rate_plan.do?type=T003). */
    public record Tab(String url, String label) {
    }
}
