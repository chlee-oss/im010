package kr.co.im010.admin.me;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.AdminPrincipal;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.core.mapper.AdminMapper;
import kr.co.im010.core.mapper.PlanAdminMapper;
import kr.co.im010.core.mapper.ReviewMapper;
import kr.co.im010.core.row.ProgramRow;

/** 로그인한 관리자 정보 · 좌측 메뉴(권한 있는 프로그램만) · 상단 처리 건수 배지. */
@RestController
public class MeController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public record MenuItem(String id, String name, String path, List<Action> actions) {
    }

    public record MenuGroup(String group, List<MenuItem> items) {
    }

    public record Badges(long reviewPending, long approvalRequested, int scheduledToday) {
    }

    public record Me(String loginId, String name, String groupName, List<MenuGroup> menus, Badges badges) {
    }

    private final AdminMapper adminMapper;
    private final ReviewMapper reviewMapper;
    private final PlanAdminMapper planMapper;

    public MeController(AdminMapper adminMapper, ReviewMapper reviewMapper, PlanAdminMapper planMapper) {
        this.adminMapper = adminMapper;
        this.reviewMapper = reviewMapper;
        this.planMapper = planMapper;
    }

    @GetMapping("/admin/api/me")
    public Me me() {
        AdminPrincipal admin = CurrentAdmin.get();
        Map<String, List<MenuItem>> groups = new LinkedHashMap<>();
        for (ProgramRow p : adminMapper.findPrograms()) {
            if (p.enabled() && admin.can(p.id(), Action.VIEW)) {
                groups.computeIfAbsent(p.menuGroup(), g -> new ArrayList<>())
                        .add(new MenuItem(p.id(), p.name(), p.path(), List.copyOf(admin.permissions().get(p.id()))));
            }
        }
        List<MenuGroup> menus = groups.entrySet().stream().map(e -> new MenuGroup(e.getKey(), e.getValue())).toList();
        LocalDate today = LocalDate.now(KST);
        Badges badges = new Badges(
                reviewMapper.countByStatus("REVIEW_PENDING"),
                reviewMapper.countByStatus("APPROVAL_REQUESTED"),
                planMapper.countScheduledOn(today.atStartOfDay(KST).toOffsetDateTime(),
                        today.plusDays(1).atStartOfDay(KST).toOffsetDateTime()));
        return new Me(admin.loginId(), admin.name(), admin.groupName(), menus, badges);
    }
}
