package kr.co.im010.admin.settings;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Csv;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.AdminListRow;
import kr.co.im010.core.row.AuditListRow;
import kr.co.im010.core.row.GroupRow;

/** 환경설정: ST-01 프로그램관리 · ST-02 관리자관리 · ST-03 권한관리 · ST-05 접속이력관리 */
@RestController
@RequestMapping("/admin/api/settings")
public class SettingsController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int AUDIT_PAGE = 100;
    private static final int DOWNLOAD_MAX = 50_000;

    public record ProgramRequest(String name, int sortOrder, boolean enabled) {
    }

    public record GroupRequest(String code, String name) {
    }

    public record IpRequest(boolean enabled, String allowlist) {
    }

    public record AuditPage(int total, int page, List<AuditListRow> items) {
    }

    private final GroupService groupService;
    private final AdminUserService adminService;
    private final SettingsMapper settingsMapper;
    private final AuditService audit;

    public SettingsController(GroupService groupService, AdminUserService adminService, SettingsMapper settingsMapper,
                              AuditService audit) {
        this.groupService = groupService;
        this.adminService = adminService;
        this.settingsMapper = settingsMapper;
        this.audit = audit;
    }

    // ---------- ST-01 ----------

    @GetMapping("/programs")
    @RequiresPermission(program = GroupService.PROGRAMS, action = Action.VIEW)
    public List<GroupService.ProgramDto> programs() {
        return groupService.programs();
    }

    @PutMapping("/programs/{id}")
    @RequiresPermission(program = GroupService.PROGRAMS, action = Action.EDIT)
    public List<GroupService.ProgramDto> updateProgram(@PathVariable String id, @RequestBody ProgramRequest body) {
        return groupService.updateProgram(id, body.name(), body.sortOrder(), body.enabled());
    }

    // ---------- ST-02 ----------

    @GetMapping("/admins")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.VIEW)
    public List<AdminListRow> admins() {
        return adminService.list();
    }

    @PostMapping("/admins")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.EDIT)
    public AdminUserService.AdminResult createAdmin(@RequestBody AdminUserService.AdminRequest body) {
        return adminService.create(body);
    }

    @PutMapping("/admins/{id}")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.EDIT)
    public AdminUserService.AdminResult updateAdmin(@PathVariable long id, @RequestBody AdminUserService.AdminRequest body) {
        return adminService.update(id, body);
    }

    @PostMapping("/admins/{id}/unlock")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.EDIT)
    public AdminUserService.AdminResult unlock(@PathVariable long id) {
        return adminService.unlock(id);
    }

    @PostMapping("/admins/{id}/reset-password")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.EDIT)
    public AdminUserService.AdminResult resetPassword(@PathVariable long id) {
        return adminService.resetPassword(id);
    }

    @PostMapping("/admins/{id}/reset-otp")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.EDIT)
    public AdminUserService.AdminResult resetOtp(@PathVariable long id) {
        return adminService.resetOtp(id);
    }

    @GetMapping("/ip-restriction")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.VIEW)
    public AdminUserService.IpSetting ipSetting(HttpServletRequest request) {
        return adminService.ipSetting(request);
    }

    @PutMapping("/ip-restriction")
    @RequiresPermission(program = AdminUserService.PROGRAM, action = Action.EDIT)
    public AdminUserService.IpSetting updateIpSetting(@RequestBody IpRequest body, HttpServletRequest request) {
        return adminService.updateIpSetting(body.enabled(), body.allowlist(), request);
    }

    // ---------- ST-03 ----------

    @GetMapping("/groups")
    @RequiresPermission(program = GroupService.GROUPS, action = Action.VIEW)
    public List<GroupRow> groups() {
        return groupService.groups();
    }

    @GetMapping("/groups/{id}")
    @RequiresPermission(program = GroupService.GROUPS, action = Action.VIEW)
    public GroupService.GroupDetail group(@PathVariable long id) {
        return groupService.detail(id);
    }

    @PostMapping("/groups")
    @RequiresPermission(program = GroupService.GROUPS, action = Action.EDIT)
    public GroupService.GroupDetail createGroup(@RequestBody GroupRequest body) {
        return groupService.create(body.code(), body.name());
    }

    @PutMapping("/groups/{id}")
    @RequiresPermission(program = GroupService.GROUPS, action = Action.EDIT)
    public GroupService.GroupDetail renameGroup(@PathVariable long id, @RequestBody GroupRequest body) {
        return groupService.rename(id, body.name());
    }

    @DeleteMapping("/groups/{id}")
    @RequiresPermission(program = GroupService.GROUPS, action = Action.EDIT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable long id) {
        groupService.delete(id);
    }

    @PutMapping("/groups/{id}/permissions")
    @RequiresPermission(program = GroupService.GROUPS, action = Action.EDIT)
    public GroupService.GroupDetail savePermissions(@PathVariable long id, @RequestBody Map<String, List<Action>> body) {
        return groupService.savePermissions(id, body);
    }

    // ---------- ST-05 (조회 · 다운로드만, 수정 · 삭제 없음) ----------

    @GetMapping("/audits")
    @RequiresPermission(program = "ST-05", action = Action.VIEW)
    public AuditPage audits(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) String kind, @RequestParam(required = false) String loginId,
                            @RequestParam(required = false) String action, @RequestParam(defaultValue = "1") int page) {
        OffsetDateTime[] range = range(from, to);
        int p = Math.max(page, 1);
        return new AuditPage(
                settingsMapper.countAudits(range[0], range[1], Texts.trim(kind), Texts.trim(loginId), Texts.trim(action)), p,
                settingsMapper.findAudits(range[0], range[1], Texts.trim(kind), Texts.trim(loginId), Texts.trim(action),
                        AUDIT_PAGE, (p - 1) * AUDIT_PAGE));
    }

    @GetMapping("/audits.csv")
    @RequiresPermission(program = "ST-05", action = Action.DOWNLOAD)
    public void auditsCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) String kind, @RequestParam(required = false) String loginId,
                          @RequestParam(required = false) String action, HttpServletResponse response) throws IOException {
        OffsetDateTime[] range = range(from, to);
        List<AuditListRow> rows = settingsMapper.findAudits(range[0], range[1], Texts.trim(kind), Texts.trim(loginId),
                Texts.trim(action), DOWNLOAD_MAX, 0);
        String name = "접속이력_" + range[0].toLocalDate() + "_" + range[1].minusDays(1).toLocalDate() + ".csv";
        audit.action("ST-05", "DOWNLOAD", name, rows.size() + "건");
        Csv.write(response, name, List.of("일시", "구분", "관리자", "프로그램", "동작", "대상", "내용", "IP"),
                rows.stream().map(r -> List.<Object>of(TS.format(r.createdAt().atZoneSameInstant(KST)), r.kind(),
                        nz(r.loginId()), nz(r.programId()), r.action(), nz(r.target()), nz(r.detail()), nz(r.ip()))).toList());
    }

    /** [from, to] 날짜 (서울) → [시작, 다음 날 0시). 기본 최근 7일, 최대 1년 */
    static OffsetDateTime[] range(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now(KST);
        LocalDate f = from != null ? from : t.minusDays(6);
        if (f.isAfter(t) || f.isBefore(t.minusYears(1))) {
            throw ApiException.badRequest("기간은 1년 이내로 지정해 주세요");
        }
        return new OffsetDateTime[] {f.atStartOfDay(KST).toOffsetDateTime(), t.plusDays(1).atStartOfDay(KST).toOffsetDateTime()};
    }

    private static Object nz(Object o) {
        return o == null ? "" : o;
    }
}
