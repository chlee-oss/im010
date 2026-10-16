package kr.co.im010.api.config;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import kr.co.im010.core.content.UploadProperties;

/** 백오피스에서 올린 이미지 (/uploads/**). 운영에서는 nginx 가 같은 폴더를 직접 내보낸다. 파일 이름이 매번 새로워 오래 캐시한다. */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadWebConfig implements WebMvcConfigurer {

    private final UploadProperties upload;

    public UploadWebConfig(UploadProperties upload) {
        this.upload = upload;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(Path.of(upload.dir()).toAbsolutePath().toUri().toString() + "/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
