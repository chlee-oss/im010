package kr.co.im010.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param frontBaseUrl 포워딩할 수 없을 때 되돌려 보낼 프런트 주소 (예: https://www.im010.co.kr)
 */
@ConfigurationProperties(prefix = "im010")
public record Im010Properties(String frontBaseUrl) {

    public Im010Properties {
        if (frontBaseUrl == null || frontBaseUrl.isBlank()) {
            frontBaseUrl = "http://localhost:5173";
        }
        if (frontBaseUrl.endsWith("/")) {
            frontBaseUrl = frontBaseUrl.substring(0, frontBaseUrl.length() - 1);
        }
    }
}
