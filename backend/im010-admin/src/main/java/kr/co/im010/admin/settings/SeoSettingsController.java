package kr.co.im010.admin.settings;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.admin.settings.SeoSettingsService.PageRequest;
import kr.co.im010.admin.settings.SeoSettingsService.PagesView;
import kr.co.im010.admin.settings.SeoSettingsService.SeoRequest;
import kr.co.im010.admin.settings.SeoSettingsService.SeoView;

/** ST-08 검색엔진설정 */
@RestController
@RequestMapping("/admin/api/settings/seo")
public class SeoSettingsController {

    private final SeoSettingsService service;

    public SeoSettingsController(SeoSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(program = SeoSettingsService.PROGRAM, action = Action.VIEW)
    public SeoView get() {
        return service.get();
    }

    @PutMapping
    @RequiresPermission(program = SeoSettingsService.PROGRAM, action = Action.EDIT)
    public SeoView save(@RequestBody SeoRequest body) {
        return service.save(body);
    }

    /** 페이지별 제목 · 설명 (/postpaid · /prepaid · /internet · /ai · /faq) */
    @GetMapping("/pages")
    @RequiresPermission(program = SeoSettingsService.PROGRAM, action = Action.VIEW)
    public PagesView pages() {
        return service.pages();
    }

    @PutMapping("/pages")
    @RequiresPermission(program = SeoSettingsService.PROGRAM, action = Action.EDIT)
    public PagesView savePages(@RequestBody List<PageRequest> body) {
        return service.savePages(body);
    }
}
