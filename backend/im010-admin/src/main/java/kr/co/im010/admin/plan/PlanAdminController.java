package kr.co.im010.admin.plan;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.admin.review.ReviewService;
import kr.co.im010.core.row.ItemValues;

/** PR-01 요금제관리 */
@RestController
@RequestMapping("/admin/api/plans")
public class PlanAdminController {

    public record IdsRequest(@NotEmpty @Size(max = 500) List<Long> ids) {
    }

    public record ScheduleRequest(@NotEmpty @Size(max = 500) List<Long> ids, @NotNull OffsetDateTime publishAt) {
    }

    public record DraftBody(@NotNull ItemValues values, Integer validDays, @Size(max = 10) List<String> tags) {
    }

    public record UrlBody(@NotNull @Size(max = 500) String url) {
    }

    private final PlanAdminService planService;

    public PlanAdminController(PlanAdminService planService) {
        this.planService = planService;
    }

    @GetMapping
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.VIEW)
    public PlanAdminService.Page list(@RequestParam(defaultValue = "POSTPAID") String type,
                                      @RequestParam(required = false) String partner,
                                      @RequestParam(required = false) String state,
                                      @RequestParam(required = false) String network,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "1") int page) {
        return planService.list(type, partner, state, network, q, page);
    }

    @GetMapping("/{id}")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.VIEW)
    public PlanAdminService.Detail detail(@PathVariable long id) {
        return planService.detail(id);
    }

    @PutMapping("/{id}/draft")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.EDIT)
    public PlanAdminService.Detail editDraft(@PathVariable long id, @Valid @RequestBody DraftBody body) {
        return planService.editDraft(id, new PlanAdminService.DraftRequest(body.values(), body.validDays(), body.tags()));
    }

    @PutMapping("/{id}/activation-url")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.EDIT)
    public PlanAdminService.Detail activationUrl(@PathVariable long id, @Valid @RequestBody UrlBody body) {
        return planService.updateActivationUrl(id, body.url());
    }

    @PostMapping("/schedule")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.APPROVE)
    public ReviewService.BulkResult schedule(@Valid @RequestBody ScheduleRequest body) {
        return planService.schedule(body.ids(), body.publishAt());
    }

    @PostMapping("/publish")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.APPROVE)
    public ReviewService.BulkResult publish(@Valid @RequestBody IdsRequest body) {
        return planService.publishNow(body.ids());
    }

    @PostMapping("/cancel-schedule")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.APPROVE)
    public ReviewService.BulkResult cancelSchedule(@Valid @RequestBody IdsRequest body) {
        return planService.cancelSchedule(body.ids());
    }

    @PostMapping("/{id}/hide")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.APPROVE)
    public PlanAdminService.Detail hide(@PathVariable long id) {
        return planService.hide(id, true);
    }

    @PostMapping("/{id}/unhide")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.APPROVE)
    public PlanAdminService.Detail unhide(@PathVariable long id) {
        return planService.hide(id, false);
    }

    @PostMapping("/{id}/rollback")
    @RequiresPermission(program = PlanAdminService.PROGRAM, action = Action.APPROVE)
    public PlanAdminService.Detail rollback(@PathVariable long id) {
        return planService.rollback(id);
    }
}
