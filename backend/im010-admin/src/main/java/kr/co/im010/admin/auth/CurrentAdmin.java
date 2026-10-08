package kr.co.im010.admin.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import kr.co.im010.admin.web.ApiException;

/** 지금 요청한 관리자. 서비스 계층에서 점검자 · 승인자 기록과 추가 권한 확인에 쓴다. */
public final class CurrentAdmin {

    private CurrentAdmin() {
    }

    public static AdminPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) {
            return p;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다");
    }

    public static void require(String program, Action action) {
        if (!get().can(program, action)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "이 작업을 할 권한이 없습니다 (" + program + " " + action + ")");
        }
    }
}
