package kr.co.im010.api.seo;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.im010.core.mapper.ContentVersionMapper;

/**
 * 미리 렌더링 데이터 지문 — 운영 서버 im010-prerender(10분마다)가 127.0.0.1:8080 으로 직접 물어보고,
 * 직전 값과 다를 때만 페이지를 다시 만든다 (요금제 게시 · 판매 종료, 알뜰AI 글 게시 · 수정, FAQ · 약관 · 검색엔진설정 변경, 날짜 변경).
 * 서버 안에서만 쓴다: nginx 가 /api/internal/ 을 막고(404), 여기서도 서버 자신이 보낸 요청이 아니면 404.
 */
@RestController
public class ContentVersionController {

    private final ContentVersionMapper mapper;

    public ContentVersionController(ContentVersionMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/api/internal/content-version")
    public ResponseEntity<String> contentVersion(HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_PLAIN)
                .body(mapper.contentVersion() + "\n");
    }

    static boolean isLoopback(String addr) {
        if (addr == null || addr.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(addr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
