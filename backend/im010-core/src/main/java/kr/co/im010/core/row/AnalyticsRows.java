package kr.co.im010.core.row;

import java.time.LocalDate;

/**
 * 대시보드 (DS-01~03) 수집 · 집계 행. 매퍼는 생성자 순서로 채우므로 SELECT 열 순서를 필드 순서와 맞춘다.
 */
public final class AnalyticsRows {

    private AnalyticsRows() {
    }

    /** 고객 화면에서 받은 이벤트 1건 (analytics_event) */
    public record Event(LocalDate day, String visitorId, String sessionId, boolean newVisitor, String name, String pageType,
                        String path, String refType, String device, Long planId, Long postId, Integer durationMs,
                        String propsJson) {
    }

    /** 방문 합계 (key = 날짜 문자열 · 유입 · 기기 등 묶음 기준) */
    public record Visit(String key, long visitors, long newVisitors, long sessions, long pageViews) {
    }

    /** 일별 추이 (searchSessions = 검색 유입 세션) */
    public record Daily(LocalDate day, long visitors, long pageViews, long planViews, long outbounds, long searchSessions) {
    }

    /** 요금제별 조회 · 클릭 · 개통하기 */
    public record PlanStat(long planId, String name, String partnerCode, String partnerName, String planType, String network,
                           String generation, Integer price, long views, long visitors, long clicks, long outbounds) {
    }

    /** 관심사 항목 × 값 */
    public record Interest(String dim, String value, long cnt) {
    }

    /** 화면 종류별 */
    public record PageStat(String pageType, long views, long visitors, long engageMs, long engageCount) {
    }

    /** 알뜰AI 글별 */
    public record PostStat(long postId, String title, String categoryName, long views, long visitors, long completes,
                           long engageMs, long engageCount, long planClicks) {
    }
}
