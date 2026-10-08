package kr.co.im010.admin.review;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;

/** BA-03 승인관리 */
@RestController
@RequestMapping("/admin/api/approvals")
public class ApprovalController {

    public record ApproveRequest(@NotEmpty @Size(max = 500) List<Long> ids, OffsetDateTime publishAt) {
    }

    public record RejectRequest(@NotEmpty @Size(max = 500) List<Long> ids, @Size(max = 500) String reason) {
    }

    private final ReviewService reviewService;
    private final ApprovalService approvalService;

    public ApprovalController(ReviewService reviewService, ApprovalService approvalService) {
        this.reviewService = reviewService;
        this.approvalService = approvalService;
    }

    @GetMapping
    @RequiresPermission(program = ApprovalService.PROGRAM, action = Action.VIEW)
    public ReviewService.Page list(@RequestParam(required = false) String partner,
                                   @RequestParam(required = false) String urlType,
                                   @RequestParam(defaultValue = "1") int page) {
        return reviewService.list("REQUESTED", null, partner, urlType, page);
    }

    @GetMapping("/{id}")
    @RequiresPermission(program = ApprovalService.PROGRAM, action = Action.VIEW)
    public ReviewService.Detail detail(@PathVariable long id) {
        return reviewService.detail(id);
    }

    @PostMapping("/approve")
    @RequiresPermission(program = ApprovalService.PROGRAM, action = Action.APPROVE)
    public ApprovalService.Result approve(@Valid @RequestBody ApproveRequest body) {
        return approvalService.approve(body.ids(), body.publishAt());
    }

    @PostMapping("/reject")
    @RequiresPermission(program = ApprovalService.PROGRAM, action = Action.APPROVE)
    public ReviewService.BulkResult reject(@Valid @RequestBody RejectRequest body) {
        return approvalService.reject(body.ids(), body.reason());
    }
}
