package kr.co.im010.admin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 매퍼 스캔은 웹 슬라이스 테스트에서 빠지도록 메인 클래스가 아닌 별도 설정에 둔다. */
@Configuration
@MapperScan("kr.co.im010.core.mapper")
public class MyBatisConfig {
}
