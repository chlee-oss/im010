package kr.co.im010.api.plan;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /** 요금제 목록. type = POSTPAID(기본) | PREPAID, partner = 제휴사 코드(선택). 가격 오름차순. */
    @GetMapping("/plans")
    public List<PlanSummary> plans(@RequestParam(required = false) String type,
                                   @RequestParam(required = false) String partner) {
        return planService.findPublished(type, partner);
    }

    /** 요금 계산기. dataGb 를 생략하면 무제한. 조건에 맞는 요금제가 없으면 204. */
    @GetMapping("/plans/cheapest")
    public ResponseEntity<PlanSummary> cheapest(@RequestParam String network,
                                                @RequestParam(required = false) BigDecimal dataGb) {
        return planService.findCheapest(Network.parse(network), dataGb)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/plans/{id}")
    public PlanDetail detail(@PathVariable long id) {
        return planService.findDetail(id);
    }

    @GetMapping("/monthly-plans")
    public List<PlanSummary> monthlyPlans() {
        return planService.findMonthlyPicks();
    }

    @GetMapping("/stats/summary")
    public Map<String, Integer> summary() {
        return Map.of("comparablePlanCount", planService.countComparable());
    }
}
