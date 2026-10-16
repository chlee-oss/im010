package kr.co.im010.api.ai;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알뜰AI (업계 소식 · 정보 콘텐츠) */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final CacheControl SHORT = CacheControl.maxAge(1, TimeUnit.MINUTES).cachePublic();

    private final AiService service;

    public AiController(AiService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<AiDtos.Category>> categories() {
        return ResponseEntity.ok().cacheControl(SHORT).body(service.categories());
    }

    /** category = 카테고리 slug (mobile · internet · misc), sort = latest | popular */
    @GetMapping("/posts")
    public ResponseEntity<AiDtos.Page> posts(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) String tag,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok().cacheControl(SHORT).body(service.list(category, tag, q, sort, page));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<AiDtos.Detail> post(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(SHORT).body(service.detail(id));
    }

    @PostMapping("/posts/{id}/view")
    public ResponseEntity<Void> view(@PathVariable long id, @RequestHeader(value = "User-Agent", required = false) String ua) {
        service.view(id, ua);
        return ResponseEntity.noContent().build();
    }
}
