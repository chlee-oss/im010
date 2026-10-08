package kr.co.im010.admin.auth;

import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.config.AdminProperties;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.core.mapper.AdminMapper;
import kr.co.im010.core.row.AdminUserRow;
import kr.co.im010.core.row.PermissionRow;
import kr.co.im010.core.row.ProgramRow;

/**
 * 관리자 로그인 (CM-01): 아이디 · 비밀번호 → OTP(처음이면 OTP 앱 등록) → (최초 로그인이면) 비밀번호 변경 → 완료.
 * 단계 상태는 세션에 두고, 모든 단계를 마쳐야 인증된다. 비밀번호 · OTP 연속 실패 5회면 30분 잠금.
 */
@Service
public class AuthService {

    public enum Stage { OTP_SETUP, OTP, PASSWORD_CHANGE, DONE }

    /** 로그인 진행 상태 (세션). pendingSecret = 등록 중인 OTP 비밀키 */
    record LoginState(long adminId, Stage stage, String pendingSecret) implements Serializable {
    }

    static final String STATE = "im010.login";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String BAD_CREDENTIALS = "아이디 또는 비밀번호가 맞지 않습니다";

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository contextRepository;
    private final AuditService audit;
    private final AdminProperties props;
    /** 없는 계정에도 비밀번호 비교 시간을 쓰게 하는 임의 해시 */
    private final String dummyHash;

    public AuthService(AdminMapper adminMapper, PasswordEncoder passwordEncoder,
                       SecurityContextRepository contextRepository, AuditService audit, AdminProperties props) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.contextRepository = contextRepository;
        this.audit = audit;
        this.props = props;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public Stage login(String loginId, String password, HttpServletRequest request) {
        AdminUserRow user = loginId == null ? null : adminMapper.findUserByLoginId(loginId.trim());
        if (user == null || !"ACTIVE".equals(user.status())) {
            passwordEncoder.matches(password == null ? "" : password, dummyHash);   // 응답 시간으로 계정 유무가 드러나지 않게
            audit.login(null, loginId, "LOGIN_FAIL", "unknown or inactive account");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        checkLocked(user);
        if (password == null || !passwordEncoder.matches(password, user.passwordHash())) {
            fail(user, "LOGIN_FAIL", "password");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        request.getSession(true);
        request.changeSessionId();
        Stage stage = user.otpEnabled() ? Stage.OTP : Stage.OTP_SETUP;
        request.getSession().setAttribute(STATE, new LoginState(user.id(), stage,
                stage == Stage.OTP_SETUP ? Totp.newSecret() : null));
        return stage;
    }

    /** OTP 앱 등록 정보 (처음 로그인할 때만). */
    public Map<String, String> otpSetup(HttpSession session) {
        LoginState state = state(session, Stage.OTP_SETUP);
        AdminUserRow user = adminMapper.findUserById(state.adminId());
        return Map.of("secret", state.pendingSecret(),
                "otpauthUri", Totp.uri(props.otpIssuer(), user.loginId(), state.pendingSecret()));
    }

    public Stage verifyOtp(String code, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        LoginState state = state(session, Stage.OTP_SETUP, Stage.OTP);
        AdminUserRow user = adminMapper.findUserById(state.adminId());
        checkLocked(user);
        boolean setup = state.stage() == Stage.OTP_SETUP;
        String secret = setup ? state.pendingSecret() : user.otpSecret();
        OptionalLong step = Totp.verify(secret, code == null ? null : code.trim(), Instant.now(), setup ? null : user.otpLastStep());
        if (step.isEmpty()) {
            fail(user, "OTP_FAIL", setup ? "otp setup" : "otp");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_OTP", "인증 번호가 맞지 않습니다");
        }
        if (setup) {
            adminMapper.enableOtp(user.id(), secret, step.getAsLong());
            audit.login(user.id(), user.loginId(), "OTP_REGISTERED", null);
        } else {
            adminMapper.updateOtpStep(user.id(), step.getAsLong());
        }
        if (user.mustChangePassword()) {
            session.setAttribute(STATE, new LoginState(user.id(), Stage.PASSWORD_CHANGE, null));
            return Stage.PASSWORD_CHANGE;
        }
        complete(user, request, response);
        return Stage.DONE;
    }

    /** 최초 로그인 비밀번호 변경 (로그인 진행 중) 또는 로그인한 관리자의 비밀번호 변경 (현재 비밀번호 확인). */
    public Stage changePassword(String currentPassword, String newPassword,
                                HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        LoginState state = session == null ? null : (LoginState) session.getAttribute(STATE);
        AdminUserRow user;
        if (state != null && state.stage() == Stage.PASSWORD_CHANGE) {
            user = adminMapper.findUserById(state.adminId());
        } else {
            user = adminMapper.findUserById(CurrentAdmin.get().id());
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_CREDENTIALS", "현재 비밀번호가 맞지 않습니다");
            }
        }
        String problem = PasswordPolicy.check(newPassword, user.loginId());
        if (problem == null && passwordEncoder.matches(newPassword, user.passwordHash())) {
            problem = "지금 쓰는 비밀번호와 다른 비밀번호를 정해 주세요";
        }
        if (problem != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", problem);
        }
        adminMapper.updatePassword(user.id(), passwordEncoder.encode(newPassword));
        audit.login(user.id(), user.loginId(), "PASSWORD_CHANGED", null);
        if (state != null && state.stage() == Stage.PASSWORD_CHANGE) {
            complete(user, request, response);
        }
        return Stage.DONE;
    }

    /** 새로 고침했을 때 화면이 이어서 보여 줄 단계. 로그인 전이면 null. */
    public Stage currentStage(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AdminPrincipal) {
            return Stage.DONE;
        }
        HttpSession session = request.getSession(false);
        LoginState state = session == null ? null : (LoginState) session.getAttribute(STATE);
        return state == null ? null : state.stage();
    }

    private void complete(AdminUserRow user, HttpServletRequest request, HttpServletResponse response) {
        adminMapper.recordLoginSuccess(user.id());
        AdminPrincipal principal = principal(user);
        request.getSession().removeAttribute(STATE);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        audit.login(user.id(), user.loginId(), "LOGIN_OK", null);
    }

    AdminPrincipal principal(AdminUserRow user) {
        Map<String, Set<Action>> perms = new HashMap<>();
        List<ProgramRow> programs = adminMapper.findPrograms();
        if (user.groupSystem()) {
            for (ProgramRow p : programs) {
                if (p.enabled()) {
                    perms.put(p.id(), actions(p.actions()));
                }
            }
        } else {
            Map<String, Set<Action>> allowed = new HashMap<>();
            for (ProgramRow p : programs) {
                if (p.enabled()) {
                    allowed.put(p.id(), actions(p.actions()));
                }
            }
            for (PermissionRow row : adminMapper.findPermissions(user.groupId())) {
                Set<Action> granted = actions(row.actions());
                Set<Action> limit = allowed.get(row.programId());
                if (limit != null) {
                    granted.retainAll(limit);   // ST-01 에 정의된 동작 범위 안에서만
                    perms.put(row.programId(), granted);
                }
            }
        }
        return new AdminPrincipal(user.id(), user.loginId(), user.name(), user.groupName(), user.groupSystem(), perms);
    }

    private static Set<Action> actions(String joined) {
        Set<Action> set = EnumSet.noneOf(Action.class);
        if (joined != null && !joined.isBlank()) {
            for (String a : joined.split("\\|")) {
                set.add(Action.valueOf(a));
            }
        }
        return set;
    }

    private void checkLocked(AdminUserRow user) {
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(OffsetDateTime.now())) {
            audit.login(user.id(), user.loginId(), "LOCKED", "until " + user.lockedUntil());
            throw new ApiException(HttpStatus.LOCKED, "LOCKED", "연속 실패로 잠겨 있습니다. "
                    + HHMM.format(user.lockedUntil().atZoneSameInstant(ZoneOffset.ofHours(9))) + " 이후에 다시 시도해 주세요");
        }
    }

    private void fail(AdminUserRow user, String action, String detail) {
        int failures = user.failedCount() + 1;
        OffsetDateTime lockedUntil = null;
        if (failures >= props.maxLoginFailures()) {
            lockedUntil = OffsetDateTime.now().plus(props.lockDuration());
            failures = 0;
        }
        adminMapper.updateLoginFailure(user.id(), failures, lockedUntil);
        audit.login(user.id(), user.loginId(), lockedUntil != null ? "LOCKED" : action, detail);
        if (lockedUntil != null) {
            throw new ApiException(HttpStatus.LOCKED, "LOCKED", "연속 " + props.maxLoginFailures() + "회 실패로 "
                    + props.lockDuration().toMinutes() + "분 동안 잠깁니다");
        }
    }

    private static LoginState state(HttpSession session, Stage... allowed) {
        LoginState state = session == null ? null : (LoginState) session.getAttribute(STATE);
        if (state != null) {
            for (Stage s : allowed) {
                if (state.stage() == s) {
                    return state;
                }
            }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_EXPIRED", "로그인을 처음부터 다시 해 주세요");
    }
}
