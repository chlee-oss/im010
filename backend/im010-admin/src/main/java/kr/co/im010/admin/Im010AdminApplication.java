package kr.co.im010.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 백오피스 API (자리만 만든 상태).
 * 다음 단계: 관리자 로그인(OTP) · 권한 · PA-01 제휴사관리 · BA-01~03 · PR-01 요금제관리.
 * 운영에서는 사내 IP · 별도 포트로만 열린다.
 */
@SpringBootApplication
@MapperScan("kr.co.im010.core.mapper")
public class Im010AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(Im010AdminApplication.class, args);
    }
}
