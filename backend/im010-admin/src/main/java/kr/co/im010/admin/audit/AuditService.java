package kr.co.im010.admin.audit;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import kr.co.im010.admin.auth.AdminPrincipal;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.core.mapper.AdminMapper;
import kr.co.im010.core.row.AuditRow;

/** 접속 · 처리 이력 기록 (ST-05). 개인정보는 남기지 않는다. */
@Service
public class AuditService {

    private final AdminMapper adminMapper;

    public AuditService(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    /** 로그인 단계 기록: LOGIN_OK · LOGIN_FAIL · OTP_FAIL · LOCKED · PASSWORD_CHANGED ... */
    public void login(Long adminId, String loginId, String action, String detail) {
        adminMapper.insertAudit(new AuditRow(adminId, loginId, "LOGIN", "CM-01", action, null, detail, clientIp()));
    }

    /** 처리 기록: 점검 · 승인 · 게시 · 수집 URL 변경 등. */
    public void action(String programId, String action, String target, String detail) {
        AdminPrincipal admin = CurrentAdmin.get();
        adminMapper.insertAudit(new AuditRow(admin.id(), admin.loginId(), "ACTION", programId, action,
                truncate(target, 200), detail, clientIp()));
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();   // nginx 뒤에서는 forward-headers-strategy 로 실제 주소
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
