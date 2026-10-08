package kr.co.im010.admin.settings;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.AdminMapper;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.GroupRow;
import kr.co.im010.core.row.PermissionRow;
import kr.co.im010.core.row.ProgramRow;

/**
 * 프로그램관리 (ST-01) · 권한관리 (ST-03). 권한은 프로그램에 정의된 동작 범위 안에서만 줄 수 있다.
 * 최고관리자 그룹은 수정 · 삭제할 수 없고, 권한 변경은 해당 관리자의 다음 로그인부터 적용된다.
 */
@Service
public class GroupService {

    static final String PROGRAMS = "ST-01";
    static final String GROUPS = "ST-03";
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    /** 끄면 백오피스 관리 자체가 막히는 프로그램 */
    private static final Set<String> ALWAYS_ON = Set.of("ST-01", "ST-03");

    public record ProgramDto(String id, String menuGroup, String name, String path, int sortOrder, boolean enabled,
                             List<Action> actions) {
    }

    public record PermissionLine(String programId, String programName, String menuGroup, boolean enabled,
                                 List<Action> allowed, List<Action> granted) {
    }

    public record GroupDetail(GroupRow group, List<PermissionLine> permissions) {
    }

    private final SettingsMapper settingsMapper;
    private final AdminMapper adminMapper;
    private final AuditService audit;

    public GroupService(SettingsMapper settingsMapper, AdminMapper adminMapper, AuditService audit) {
        this.settingsMapper = settingsMapper;
        this.adminMapper = adminMapper;
        this.audit = audit;
    }

    public List<ProgramDto> programs() {
        return adminMapper.findPrograms().stream().map(p -> new ProgramDto(p.id(), p.menuGroup(), p.name(), p.path(),
                p.sortOrder(), p.enabled(), List.copyOf(actions(p.actions())))).toList();
    }

    @Transactional
    public List<ProgramDto> updateProgram(String id, String name, int sortOrder, boolean enabled) {
        String n = Texts.trim(name);
        if (n == null || n.length() > 50) {
            throw ApiException.badRequest("프로그램명은 1 ~ 50자여야 합니다");
        }
        if (!enabled && ALWAYS_ON.contains(id)) {
            throw ApiException.conflict(id + " 는 끌 수 없습니다");
        }
        if (settingsMapper.updateProgram(id, n, sortOrder, enabled) == 0) {
            throw ApiException.notFound("프로그램");
        }
        audit.action(PROGRAMS, "PROGRAM_UPDATE", id, n + (enabled ? "" : " (미사용)"));
        return programs();
    }

    public List<GroupRow> groups() {
        return settingsMapper.findGroups();
    }

    public GroupDetail detail(long id) {
        GroupRow group = find(id);
        Map<String, Set<Action>> granted = new HashMap<>();
        for (PermissionRow row : adminMapper.findPermissions(id)) {
            granted.put(row.programId(), actions(row.actions()));
        }
        List<PermissionLine> lines = new ArrayList<>();
        for (ProgramRow p : adminMapper.findPrograms()) {
            Set<Action> allowed = actions(p.actions());
            Set<Action> g = group.system() ? allowed : granted.getOrDefault(p.id(), Set.of());
            lines.add(new PermissionLine(p.id(), p.name(), p.menuGroup(), p.enabled(), List.copyOf(allowed),
                    allowed.stream().filter(g::contains).toList()));
        }
        return new GroupDetail(group, lines);
    }

    @Transactional
    public GroupDetail create(String code, String name) {
        String c = Texts.trim(code) == null ? null : code.trim().toUpperCase();
        if (c == null || !CODE.matcher(c).matches()) {
            throw ApiException.badRequest("코드는 영문 대문자로 시작하는 영문 대문자 · 숫자 · _ 2 ~ 30자여야 합니다");
        }
        if (settingsMapper.findGroups().stream().anyMatch(g -> g.code().equals(c))) {
            throw ApiException.conflict("이미 있는 코드입니다");
        }
        long id = settingsMapper.insertGroup(c, groupName(name));
        audit.action(GROUPS, "GROUP_CREATE", c, name);
        return detail(id);
    }

    @Transactional
    public GroupDetail rename(long id, String name) {
        GroupRow g = editable(id);
        settingsMapper.updateGroupName(id, groupName(name));
        audit.action(GROUPS, "GROUP_RENAME", g.code(), name);
        return detail(id);
    }

    @Transactional
    public void delete(long id) {
        CurrentAdmin.require(GROUPS, Action.DELETE);
        GroupRow g = editable(id);
        if (g.adminCount() > 0) {
            throw ApiException.conflict("이 그룹에 속한 관리자가 " + g.adminCount() + "명 있어 삭제할 수 없습니다");
        }
        settingsMapper.deleteGroup(id);
        audit.action(GROUPS, "GROUP_DELETE", g.code(), g.name());
    }

    /** 권한표 저장: programId → 동작. 프로그램에 없는 동작은 저장하지 않는다. */
    @Transactional
    public GroupDetail savePermissions(long id, Map<String, List<Action>> permissions) {
        GroupRow g = editable(id);
        Map<String, Set<Action>> allowed = new HashMap<>();
        for (ProgramRow p : adminMapper.findPrograms()) {
            allowed.put(p.id(), actions(p.actions()));
        }
        settingsMapper.deletePermissions(id);
        List<String> summary = new ArrayList<>();
        for (Map.Entry<String, List<Action>> e : permissions.entrySet()) {
            Set<Action> limit = allowed.get(e.getKey());
            if (limit == null) {
                throw ApiException.badRequest("없는 프로그램: " + e.getKey());
            }
            Set<Action> granted = EnumSet.noneOf(Action.class);
            granted.addAll(e.getValue());
            granted.retainAll(limit);
            if (!granted.isEmpty() && !granted.contains(Action.VIEW)) {
                granted.add(Action.VIEW);   // 조회 없이 다른 동작만 줄 수는 없다
            }
            if (!granted.isEmpty()) {
                settingsMapper.insertPermission(id, e.getKey(), granted.stream().map(Enum::name).toArray(String[]::new));
                summary.add(e.getKey() + ":" + granted.stream().map(Enum::name).reduce((a, b) -> a + "/" + b).orElse(""));
            }
        }
        audit.action(GROUPS, "PERMISSION_SAVE", g.code(), String.join(", ", summary));
        return detail(id);
    }

    private GroupRow editable(long id) {
        GroupRow g = find(id);
        if (g.system()) {
            throw ApiException.conflict("최고관리자 그룹은 바꿀 수 없습니다");
        }
        return g;
    }

    private GroupRow find(long id) {
        GroupRow g = settingsMapper.findGroup(id);
        if (g == null) {
            throw ApiException.notFound("권한 그룹");
        }
        return g;
    }

    private static String groupName(String name) {
        String n = Texts.trim(name);
        if (n == null || n.length() > 50) {
            throw ApiException.badRequest("그룹 이름은 1 ~ 50자여야 합니다");
        }
        return n;
    }

    static Set<Action> actions(String joined) {
        Set<Action> set = EnumSet.noneOf(Action.class);
        for (String a : Texts.split(joined)) {
            set.add(Action.valueOf(a));
        }
        return set;
    }
}
