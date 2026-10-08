package kr.co.im010.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/** 고객용 API — React 프런트가 호출하는 조회 API와 /go 포워딩. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class Im010ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(Im010ApiApplication.class, args);
    }
}
