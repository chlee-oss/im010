package kr.co.im010.batch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 수집 배치: 제휴사 요금제 페이지 크롤링(Jsoup) → 변경 감지 → 판매 종료(보호 조건 포함) → BA-02 점검 대기 생성.
 * 스케줄은 DB(crawl_schedule)에서 읽고, 작업은 crawl_job 큐로 처리한다. DB 마이그레이션은 im010-api 가 맡는다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan("kr.co.im010.core.mapper")
public class Im010BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(Im010BatchApplication.class, args);
    }
}
