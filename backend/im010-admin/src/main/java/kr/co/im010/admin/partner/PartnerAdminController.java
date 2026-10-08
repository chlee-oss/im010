package kr.co.im010.admin.partner;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.core.row.CrawlRunAdminRow;
import kr.co.im010.core.row.PartnerAdminRow;

/** PA-01 제휴사관리 [알뜰폰] · BA-01 스케줄관리 */
@RestController
@RequestMapping("/admin/api")
public class PartnerAdminController {

    public record TestRequest(@NotBlank @Size(max = 500) String url, @Size(max = 50) String label) {
    }

    public record TabRequest(@NotBlank String status) {
    }

    private static final String PA = PartnerAdminService.PROGRAM;
    private static final String BA = PartnerAdminService.SCHEDULE_PROGRAM;

    private final PartnerAdminService service;

    public PartnerAdminController(PartnerAdminService service) {
        this.service = service;
    }

    @GetMapping("/partners")
    @RequiresPermission(program = PA, action = Action.VIEW)
    public List<PartnerAdminRow> partners() {
        return service.list();
    }

    @GetMapping("/partners/{code}")
    @RequiresPermission(program = PA, action = Action.VIEW)
    public PartnerAdminService.Detail partner(@PathVariable String code) {
        return service.detail(code);
    }

    @PostMapping("/partners")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.Detail create(@RequestBody PartnerAdminService.PartnerRequest body) {
        return service.create(body);
    }

    @PutMapping("/partners/{code}")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.Detail update(@PathVariable String code, @RequestBody PartnerAdminService.PartnerRequest body) {
        return service.update(code, body);
    }

    @PostMapping("/partners/{code}/urls")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.Detail addUrl(@PathVariable String code, @RequestBody PartnerAdminService.UrlRequest body) {
        return service.addUrl(code, body);
    }

    @PutMapping("/partners/{code}/urls/{id}")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.Detail updateUrl(@PathVariable String code, @PathVariable long id,
                                                @RequestBody PartnerAdminService.UrlRequest body) {
        return service.updateUrl(code, id, body);
    }

    @DeleteMapping("/partners/{code}/urls/{id}")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.Detail deleteUrl(@PathVariable String code, @PathVariable long id,
                                                @RequestParam(defaultValue = "true") boolean hidePlans) {
        return service.deleteUrl(code, id, hidePlans);
    }

    @PostMapping("/partners/{code}/urls/test")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.TestResult test(@PathVariable String code, @Valid @RequestBody TestRequest body) {
        return service.test(code, body.url(), body.label());
    }

    @PutMapping("/partners/{code}/tabs/{id}")
    @RequiresPermission(program = PA, action = Action.EDIT)
    public PartnerAdminService.Detail tab(@PathVariable String code, @PathVariable long id,
                                          @Valid @RequestBody TabRequest body) {
        return service.updateTab(code, id, body.status());
    }

    @GetMapping("/schedules")
    @RequiresPermission(program = BA, action = Action.VIEW)
    public List<PartnerAdminRow> schedules() {
        return service.list();
    }

    @PutMapping("/schedules/{code}")
    @RequiresPermission(program = BA, action = Action.EDIT)
    public PartnerAdminRow updateSchedule(@PathVariable String code, @RequestBody PartnerAdminService.ScheduleRequest body) {
        return service.updateSchedule(code, body);
    }

    @PostMapping("/schedules/{code}/run")
    @RequiresPermission(program = BA, action = Action.EDIT)
    public PartnerAdminRow runNow(@PathVariable String code) {
        return service.runNow(code);
    }

    @GetMapping("/runs")
    @RequiresPermission(program = BA, action = Action.VIEW)
    public List<CrawlRunAdminRow> runs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) String partner,
                                       @RequestParam(required = false) String urlType,
                                       @RequestParam(required = false) String result) {
        return service.runs(from, to, partner, urlType, result);
    }
}
