package kr.co.im010.api.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 매퍼 스캔을 별도 설정으로 둬서 웹 슬라이스 테스트(@WebMvcTest)가 DB 없이 뜨게 한다. */
@Configuration(proxyBeanMethods = false)
@MapperScan("kr.co.im010.core.mapper")
public class MyBatisConfig {
}
