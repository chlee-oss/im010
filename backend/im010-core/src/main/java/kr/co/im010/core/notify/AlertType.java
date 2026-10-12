package kr.co.im010.core.notify;

/**
 * 알림 종류 (백오피스 7장). defaultChannel = 새 메신저 채널 · 메일 수신 설정의 기본 선택.
 */
public enum AlertType {
    CRAWL_SUMMARY("수집 결과 요약", true),
    CRAWL_FAILURE("수집 실패 · 이상", true),
    PLAN_ENDED("판매 종료 처리", true),
    MONTHLY_REMOVED("메인 노출 제외", true),
    REVIEW_DELAY("점검 · 승인 지연", true),
    PUBLISH_FAILURE("게시 예약 실패", true),
    FORWARD_NO_URL("포워딩 URL 없음", true),
    STALE_DATA("기준일 경과", false),
    TAB_CHANGED("사이트 탭 변경", true);

    private final String label;
    private final boolean messengerDefault;

    AlertType(String label, boolean messengerDefault) {
        this.label = label;
        this.messengerDefault = messengerDefault;
    }

    public String label() {
        return label;
    }

    public boolean messengerDefault() {
        return messengerDefault;
    }
}
