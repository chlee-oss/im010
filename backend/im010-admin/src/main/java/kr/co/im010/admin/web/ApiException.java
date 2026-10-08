package kr.co.im010.admin.web;

import org.springframework.http.HttpStatus;

/** 화면에 그대로 보여 줄 메시지를 담은 오류. code 는 화면 분기용 (예: LOCKED, PASSWORD_POLICY). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + "을(를) 찾을 수 없습니다");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
