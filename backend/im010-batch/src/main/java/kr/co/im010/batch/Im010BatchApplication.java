package kr.co.im010.batch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 수집 배치 (자리만 만든 상태).
 * 다음 단계: 제휴사 페이지 구조 확인 → 크롤러(Jsoup 또는 Playwright) → 변경 감지 → 판매 종료(보호 조건 포함)
 * → BA-02 점검 대기 생성, Quartz(JDBC 저장)로 제휴사별 스케줄 · 게시 예약 실행.
 */
@SpringBootApplication
@MapperScan("kr.co.im010.core.mapper")
public class Im010BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(Im010BatchApplication.class, args);
    }
}
