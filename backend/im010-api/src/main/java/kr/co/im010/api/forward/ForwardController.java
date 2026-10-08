package kr.co.im010.api.forward;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /go 이동 주소. 프런트는 제휴사 URL을 직접 걸지 않고 이 주소를 거친다. */
@RestController
public class ForwardController {

    private final ForwardService forwardService;

    public ForwardController(ForwardService forwardService) {
        this.forwardService = forwardService;
    }

    /** 개통하기 → 제휴사 개통 URL. */
    @GetMapping("/go/{planId}")
    public ResponseEntity<Void> plan(@PathVariable long planId, @RequestParam(required = false) String from) {
        return redirect(forwardService.forwardPlan(planId, from));
    }

    /** 인터넷 상담 신청 → 제휴업체 신청 페이지. */
    @GetMapping("/go/internet/{productId}")
    public ResponseEntity<Void> internet(@PathVariable long productId, @RequestParam(required = false) String from) {
        return redirect(forwardService.forwardInternet(productId, from));
    }

    private static ResponseEntity<Void> redirect(java.net.URI uri) {
        // 302: 브라우저 · 프록시가 이동 결과를 캐시하지 않도록 (이동할 때마다 기록)
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).header("Cache-Control", "no-store").build();
    }
}
