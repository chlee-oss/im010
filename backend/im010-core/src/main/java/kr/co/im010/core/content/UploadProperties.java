package kr.co.im010.core.content;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 업로드 파일 (알뜰AI 썸네일 · 본문 이미지). 백오피스가 저장하고 고객 API · nginx 가 /uploads/** 로 내보낸다.
 *
 * @param dir      저장 폴더 (운영: /opt/im010/uploads, 로컬: 사용자 폴더 아래)
 * @param maxBytes 파일 하나 최대 크기 (기본 5MB)
 */
@ConfigurationProperties(prefix = "im010.upload")
public record UploadProperties(
        @DefaultValue("/opt/im010/uploads") String dir,
        @DefaultValue("5242880") long maxBytes
) {
}
