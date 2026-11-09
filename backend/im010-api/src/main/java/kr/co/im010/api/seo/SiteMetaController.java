package kr.co.im010.api.seo;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.api.seo.SiteMetaService.Page;
import kr.co.im010.core.content.SiteMeta;

/**
 * 검색엔진설정(ST-08) 값.
 * - GET /api/site-meta: 고객 화면(브라우저 탭 제목) · 미리 렌더링(prerender.mjs)이 읽는다
 * - GET /api/site-html/home|shell: nginx 가 메인(/) · 기본 틀을 여기로 넘긴다. 404 면 nginx 가 정적 파일로 응답
 */
@RestController
public class SiteMetaController {

    private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final CacheControl META_CACHE = CacheControl.maxAge(1, TimeUnit.MINUTES).cachePublic();

    /** 화면 · 미리 렌더링에 그대로 쓰는 값 (OG 는 비어 있으면 사이트 제목 · 설명으로 채운 값) */
    public record SiteMetaView(String title, String description, String ogTitle, String ogDescription) {

        static SiteMetaView of(SiteMeta m) {
            return new SiteMetaView(m.title(), m.description(), m.effectiveOgTitle(), m.effectiveOgDescription());
        }
    }

    private final SiteMetaService service;

    public SiteMetaController(SiteMetaService service) {
        this.service = service;
    }

    @GetMapping("/api/site-meta")
    public ResponseEntity<SiteMetaView> meta() {
        return ResponseEntity.ok().cacheControl(META_CACHE).body(SiteMetaView.of(service.current()));
    }

    @GetMapping("/api/site-html/{page}")
    public ResponseEntity<String> html(@PathVariable String page) {
        Page p = switch (page) {
            case "home" -> Page.HOME;
            case "shell" -> Page.SHELL;
            default -> null;
        };
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        return service.html(p)
                .map(h -> ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(HTML_UTF8).body(h))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
