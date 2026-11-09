package kr.co.im010.api.seo;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** https://www.im010.co.kr/sitemap.xml — nginx 가 이 주소만 api 로 넘긴다 (deploy/nginx/im010.conf) */
@RestController
public class SitemapController {

    private static final MediaType XML_UTF8 = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private static final CacheControl CACHE = CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic();

    private final SitemapService service;

    public SitemapController(SitemapService service) {
        this.service = service;
    }

    // produces 는 두지 않는다 — Accept 가 무엇이든(브라우저 text/html 등) 406 없이 XML 을 준다
    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().cacheControl(CACHE).contentType(XML_UTF8).body(service.xml());
    }
}
