package kr.co.im010.admin.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 백오피스 설정.
 *
 * @param bootstrapLoginId  관리자가 한 명도 없을 때 만들 최고관리자 아이디
 * @param bootstrapPassword 그 초기 비밀번호 (환경 변수로만 전달, 최초 로그인 때 변경 강제). 비우면 만들지 않음
 * @param otpIssuer         OTP 앱에 표시할 이름
 * @param maxLoginFailures  잠금까지 허용하는 연속 실패 수 (CM-01: 5회)
 * @param lockDuration      잠금 시간 (CM-01: 30분)
 * @param rollbackWindow    게시 후 롤백 가능 기간 (결정 #27: 7일)
 * @param userAgent         수집 URL [테스트] 때 접속 정보
 */
@ConfigurationProperties(prefix = "im010.admin")
public record AdminProperties(
        @DefaultValue("admin") String bootstrapLoginId,
        String bootstrapPassword,
        @DefaultValue("im010 admin") String otpIssuer,
        @DefaultValue("5") int maxLoginFailures,
        @DefaultValue("30m") Duration lockDuration,
        @DefaultValue("7d") Duration rollbackWindow,
        @DefaultValue("Mozilla/5.0 (compatible; im010-crawler/1.0; +https://www.im010.co.kr)") String userAgent
) {
}
