package kr.co.im010.batch;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 수집 배치 설정 (백오피스 6장 수집 배치 규칙).
 *
 * @param userAgent            접속 정보에 im010 수집기임을 표시 (6.1)
 * @param requestDelay         같은 제휴사 요청 사이 간격 (6.1: 3초 이상)
 * @param timeout              페이지 한 건 응답 대기
 * @param schedulerEnabled     스케줄 실행 여부. 로컬에서는 끄고 runNow 로 실행
 * @param zone                 수집 기준일 · 스케줄 시각의 시간대
 * @param retryDelay           실패 시 재시도 간격 (BA-01: 30분)
 * @param maxAttempts          최초 1회 + 재시도 2회
 * @param retentionDays        수집 기록(파싱 값) 보관 기간 (결정 #28)
 * @param minKeepRatio         직전 정상 수집 대비 이 비율 미만이면 수집 이상 (6.3: 50% 넘게 감소)
 * @param maxFieldFailureRatio 필수 항목(요금제명 · 요금) 추출 실패 비율 상한 (6.3: 20%)
 * @param priceJumpRatio       전 버전 대비 요금 변동 경고 기준 (3.2: ±30%)
 * @param runNow               시작하자마자 수집할 제휴사 코드 (로컬 · 수동 실행용). 지정하면 수집 후 종료
 */
@ConfigurationProperties(prefix = "im010.crawl")
public record CrawlProperties(
        @DefaultValue("Mozilla/5.0 (compatible; im010-crawler/1.0; +https://www.im010.co.kr)") String userAgent,
        @DefaultValue("3s") Duration requestDelay,
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("Asia/Seoul") ZoneId zone,
        @DefaultValue("30m") Duration retryDelay,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("30") int retentionDays,
        @DefaultValue("0.5") double minKeepRatio,
        @DefaultValue("0.2") double maxFieldFailureRatio,
        @DefaultValue("0.3") double priceJumpRatio,
        @DefaultValue List<String> runNow
) {
}
