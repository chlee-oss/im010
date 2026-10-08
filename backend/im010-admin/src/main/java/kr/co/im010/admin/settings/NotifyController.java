package kr.co.im010.admin.settings;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;

/** 알림 (백오피스 7장): ST-02 [알림 채널] · [알림 이력], 내 알림 설정 (로그인한 관리자 누구나) */
@RestController
public class NotifyController {

    public record MyNotifyRequest(String email, List<String> mailAlerts) {
    }

    public record TestRequest(String alertType) {
    }

    private final NotifyAdminService service;

    public NotifyController(NotifyAdminService service) {
        this.service = service;
    }

    @GetMapping("/admin/api/settings/notify/channels")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.VIEW)
    public Map<String, Object> channels() {
        return Map.of("channels", service.channels(), "types", NotifyAdminService.types());
    }

    @PostMapping("/admin/api/settings/notify/channels")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.EDIT)
    public List<NotifyAdminService.ChannelDto> create(@RequestBody NotifyAdminService.ChannelRequest body) {
        return service.createChannel(body);
    }

    @PutMapping("/admin/api/settings/notify/channels/{id}")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.EDIT)
    public List<NotifyAdminService.ChannelDto> update(@PathVariable long id, @RequestBody NotifyAdminService.ChannelRequest body) {
        return service.updateChannel(id, body);
    }

    @DeleteMapping("/admin/api/settings/notify/channels/{id}")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.EDIT)
    public List<NotifyAdminService.ChannelDto> delete(@PathVariable long id) {
        return service.deleteChannel(id);
    }

    @PostMapping("/admin/api/settings/notify/channels/{id}/test")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.EDIT)
    public Map<String, String> test(@PathVariable long id) {
        return Map.of("message", service.testChannel(id));
    }

    @PostMapping("/admin/api/settings/notify/test-alert")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.EDIT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void testAlert(@RequestBody TestRequest body) {
        service.enqueueTest(body.alertType());
    }

    @GetMapping("/admin/api/settings/notify/history")
    @RequiresPermission(program = NotifyAdminService.PROGRAM, action = Action.VIEW)
    public NotifyAdminService.HistoryPage history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String alertType, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page) {
        OffsetDateTime[] range = SettingsController.range(from, to);
        return service.history(range[0], range[1], alertType, status, page);
    }

    @GetMapping("/admin/api/me/notify")
    public NotifyAdminService.MyNotify mine() {
        return service.mine();
    }

    @PutMapping("/admin/api/me/notify")
    public NotifyAdminService.MyNotify updateMine(@RequestBody MyNotifyRequest body) {
        return service.updateMine(body.email(), body.mailAlerts());
    }
}
