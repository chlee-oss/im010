package kr.co.im010.core.row;

/** 포워딩 이동 기록 한 건 (개인정보 없음). */
public record ForwardLog(
        String kind,        // PLAN | INTERNET
        long targetId,
        String partnerRef,
        String planType,
        String targetUrl,
        String fromPage,
        String result       // FORWARDED | ENDED | NO_URL | HIDDEN
) {
}
