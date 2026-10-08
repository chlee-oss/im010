package kr.co.im010.admin.settings;

import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.AdminPrincipal;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.auth.IpRestrictionFilter;
import kr.co.im010.admin.auth.IpRules;
import kr.co.im010.admin.auth.PasswordPolicy;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.SettingsMapper;
import kr.co.im010.core.row.AdminListRow;
import kr.co.im010.core.row.GroupRow;

/**
 * 관리자관리 (ST-02): 계정 발급 · 수정 · 퇴사, 잠금 해제 · 비밀번호 초기화 · OTP 초기화(최고관리자만), 사내 IP 제한 설정.
 * 발급 · 초기화 때 만든 임시 비밀번호는 응답으로 한 번만 보여 주고 저장하지 않는다 (해시만 저장).
 */
@Service
public class AdminUserService {

    static final String PROGRAM = "ST-02";
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,29}$");
    private static final String TEMP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record AdminRequest(String loginId, String name, String dept, String phone, Long groupId, String status) {
    }

    /** tempPassword: 발급 · 초기화 때만 (화면에서 한 번 보여 주고 버린다) */
    public record AdminResult(AdminListRow admin, String tempPassword) {
    }

    public record IpSetting(boolean enabled, String allowlist, String yourIp) {
    }

    private final SettingsMapper settingsMapper;
    private final PasswordEncoder passwordEncoder;
    private final IpRestrictionFilter ipFilter;
    private final AuditService audit;

    public AdminUserService(SettingsMapper settingsMapper, PasswordEncoder passwordEncoder, IpRestrictionFilter ipFilter,
                            AuditService audit) {
        this.settingsMapper = settingsMapper;
        this.passwordEncoder = passwordEncoder;
        this.ipFilter = ipFilter;
        this.audit = audit;
    }

    public List<AdminListRow> list() {
        return settingsMapper.findAdmins();
    }

    @Transactional
    public AdminResult create(AdminRequest req) {
        String loginId = Texts.trim(req.loginId());
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw ApiException.badRequest("아이디는 영문 소문자 · 숫자 · . _ - 3 ~ 30자여야 합니다");
        }
        if (settingsMapper.existsLoginId(loginId)) {
            throw ApiException.conflict("이미 있는 아이디입니다");
        }
        GroupRow group = group(req.groupId());
        if (group.system()) {
            CurrentAdmin.requireSuper();
        }
        String temp = tempPassword(loginId);
        long id = settingsMapper.insertAdmin(loginId, name(req.name()), len(req.dept(), 50), len(req.phone(), 30),
                group.id(), passwordEncoder.encode(temp));
        audit.action(PROGRAM, "ADMIN_CREATE", loginId, group.name());
        return new AdminResult(settingsMapper.findAdmin(id), temp);
    }

    @Transactional
    public AdminResult update(long id, AdminRequest req) {
        AdminListRow admin = find(id);
        GroupRow group = group(req.groupId());
        String status = "RETIRED".equals(req.status()) ? "RETIRED" : "ACTIVE";
        AdminPrincipal me = CurrentAdmin.get();
        boolean superChange = admin.groupSystem() != group.system() || (admin.groupSystem() && !status.equals(admin.status()));
        if (superChange) {
            CurrentAdmin.requireSuper();   // 최고관리자 그룹으로 넣고 빼는 것은 최고관리자만
        }
        if (admin.id() == me.id() && (status.equals("RETIRED") || admin.groupId() != group.id())) {
            throw ApiException.conflict("자기 계정의 그룹 · 상태는 바꿀 수 없습니다");
        }
        if (admin.groupSystem() && "ACTIVE".equals(admin.status()) && (!group.system() || status.equals("RETIRED"))
                && settingsMapper.countActiveSuperAdmins() <= 1) {
            throw ApiException.conflict("마지막 최고관리자는 그룹을 바꾸거나 퇴사 처리할 수 없습니다");
        }
        settingsMapper.updateAdmin(id, name(req.name()), len(req.dept(), 50), len(req.phone(), 30), group.id(), status);
        audit.action(PROGRAM, "ADMIN_UPDATE", admin.loginId(), group.name() + " · " + status);
        return new AdminResult(find(id), null);
    }

    @Transactional
    public AdminResult unlock(long id) {
        CurrentAdmin.requireSuper();
        AdminListRow admin = find(id);
        settingsMapper.unlock(id);
        audit.action(PROGRAM, "ADMIN_UNLOCK", admin.loginId(), null);
        return new AdminResult(find(id), null);
    }

    @Transactional
    public AdminResult resetPassword(long id) {
        CurrentAdmin.requireSuper();
        AdminListRow admin = find(id);
        String temp = tempPassword(admin.loginId());
        settingsMapper.resetPassword(id, passwordEncoder.encode(temp));
        audit.action(PROGRAM, "ADMIN_PASSWORD_RESET", admin.loginId(), null);
        return new AdminResult(find(id), temp);
    }

    @Transactional
    public AdminResult resetOtp(long id) {
        CurrentAdmin.requireSuper();
        AdminListRow admin = find(id);
        settingsMapper.resetOtp(id);
        audit.action(PROGRAM, "ADMIN_OTP_RESET", admin.loginId(), null);
        return new AdminResult(find(id), null);
    }

    public IpSetting ipSetting(HttpServletRequest request) {
        return new IpSetting("true".equals(settingsMapper.findSetting(IpRestrictionFilter.ENABLED)),
                settingsMapper.findSetting(IpRestrictionFilter.ALLOWLIST), request.getRemoteAddr());
    }

    /** 켤 때는 지금 접속한 주소가 허용 목록에 있어야 한다 (스스로 막히지 않게). */
    @Transactional
    public IpSetting updateIpSetting(boolean enabled, String allowlist, HttpServletRequest request) {
        CurrentAdmin.requireSuper();
        String text = allowlist == null ? "" : allowlist.strip();
        if (text.length() > 4000) {
            throw ApiException.badRequest("허용 목록이 너무 깁니다");
        }
        IpRules rules;
        try {
            rules = IpRules.parse(text);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("IP 형식이 올바르지 않습니다: " + e.getMessage());
        }
        if (enabled && !rules.allows(request.getRemoteAddr())) {
            throw new ApiException(HttpStatus.CONFLICT, "IP_SELF_BLOCK",
                    "지금 접속한 주소(" + request.getRemoteAddr() + ")가 허용 목록에 없어 켤 수 없습니다");
        }
        String by = CurrentAdmin.get().loginId();
        settingsMapper.updateSetting(IpRestrictionFilter.ALLOWLIST, text, by);
        settingsMapper.updateSetting(IpRestrictionFilter.ENABLED, String.valueOf(enabled), by);
        ipFilter.refresh();
        audit.action(PROGRAM, "IP_RESTRICTION", enabled ? "ON" : "OFF", text.replace('\n', ' '));
        return ipSetting(request);
    }

    /** 비밀번호 규칙에 맞는 임시 비밀번호 (대문자 · 소문자 · 숫자 · 특수문자 포함 14자) */
    static String tempPassword(String loginId) {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(TEMP_CHARS.charAt(RANDOM.nextInt(TEMP_CHARS.length())));
            }
            sb.insert(RANDOM.nextInt(sb.length()), "!#%*".charAt(RANDOM.nextInt(4)));
            sb.insert(RANDOM.nextInt(sb.length()), (char) ('2' + RANDOM.nextInt(8)));
            String p = sb.toString();
            if (PasswordPolicy.check(p, loginId) == null) {
                return p;
            }
        }
    }

    private GroupRow group(Long groupId) {
        GroupRow g = groupId == null ? null : settingsMapper.findGroup(groupId);
        if (g == null) {
            throw ApiException.badRequest("권한 그룹을 골라 주세요");
        }
        return g;
    }

    private AdminListRow find(long id) {
        AdminListRow a = settingsMapper.findAdmin(id);
        if (a == null) {
            throw ApiException.notFound("관리자");
        }
        return a;
    }

    private static String name(String name) {
        String n = Texts.trim(name);
        if (n == null || n.length() > 50) {
            throw ApiException.badRequest("이름은 1 ~ 50자여야 합니다");
        }
        return n;
    }

    private static String len(String s, int max) {
        String t = Texts.trim(s);
        if (t != null && t.length() > max) {
            throw ApiException.badRequest(max + "자 이하로 입력해 주세요");
        }
        return t;
    }
}
