package kr.co.im010.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 백오피스 API (8081). 관리자 로그인(비밀번호 + OTP) · 프로그램별 권한 ·
 * PA-01 제휴사관리 · PR-01 요금제관리 · BA-01 스케줄관리 · BA-02 요금제배치관리 · BA-03 승인관리.
 * 운영에서는 사내 전용 주소로만 연다 (deploy/nginx). DB 마이그레이션은 im010-api 가 맡는다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Im010AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(Im010AdminApplication.class, args);
    }
}
