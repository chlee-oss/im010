package kr.co.im010.admin.review;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import kr.co.im010.core.row.ItemValues;

/** BA-02 요금제배치관리 */
@RestController
@RequestMapping("/admin/api/batch-items")
public class ReviewController {

    public record IdsRequest(@NotEmpty @Size(max = 500) List<Long> ids) {
    }

    public record ExcludeRequest(@NotEmpty @Size(max = 500) List<Long> ids, @Size(max = 500) String reason) {
    }

    public record UpdateRequest(@NotNull @Valid ItemValues values, @Size(max = 500) String memo) {
    }

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @RequiresPermission(program = ReviewService.PROGRAM, action = Action.VIEW)
    public ReviewService.Page list(@RequestParam(required = false) String view,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate collectedOn,
                                   @RequestParam(required = false) String partner,
                                   @RequestParam(required = false) String urlType,
                                   @RequestParam(defaultValue = "1") int page) {
        return reviewService.list(view, collectedOn, partner, urlType, page);
    }

    @GetMapping("/{id}")
    @RequiresPermission(program = ReviewService.PROGRAM, action = Action.VIEW)
    public ReviewService.Detail detail(@PathVariable long id) {
        return reviewService.detail(id);
    }

    @PutMapping("/{id}")
    @RequiresPermission(program = ReviewService.PROGRAM, action = Action.EDIT)
    public ItemDto update(@PathVariable long id, @Valid @RequestBody UpdateRequest body) {
        return reviewService.update(id, body.values(), body.memo());
    }

    @PostMapping("/review")
    @RequiresPermission(program = ReviewService.PROGRAM, action = Action.REVIEW)
    public ReviewService.BulkResult review(@Valid @RequestBody IdsRequest body) {
        return reviewService.review(body.ids());
    }

    @PostMapping("/exclude")
    @RequiresPermission(program = ReviewService.PROGRAM, action = Action.REVIEW)
    public ReviewService.BulkResult exclude(@Valid @RequestBody ExcludeRequest body) {
        return reviewService.exclude(body.ids(), body.reason());
    }

    @PostMapping("/{id}/resume")
    @RequiresPermission(program = ReviewService.PROGRAM, action = Action.REVIEW)
    public ItemDto resume(@PathVariable long id) {
        return reviewService.requestResume(id);
    }
}
