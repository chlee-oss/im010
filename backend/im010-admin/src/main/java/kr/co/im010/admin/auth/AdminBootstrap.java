package kr.co.im010.admin.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import kr.co.im010.admin.config.AdminProperties;
import kr.co.im010.core.mapper.AdminMapper;

/**
 * 관리자가 한 명도 없으면 최고관리자 계정을 하나 만든다 (설치 직후 1회).
 * 초기 비밀번호는 IM010_ADMIN_BOOTSTRAP_PASSWORD 로만 받고, 첫 로그인 때 OTP 등록 · 비밀번호 변경을 강제한다.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties props;

    public AdminBootstrap(AdminMapper adminMapper, PasswordEncoder passwordEncoder, AdminProperties props) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createFirstAdmin() {
        if (adminMapper.countUsers() > 0) {
            return;
        }
        if (props.bootstrapPassword() == null || props.bootstrapPassword().isBlank()) {
            log.warn("관리자 계정이 없습니다. IM010_ADMIN_BOOTSTRAP_PASSWORD 를 지정해 다시 시작하면 최고관리자 계정을 만듭니다");
            return;
        }
        adminMapper.insertUser(props.bootstrapLoginId(), "최고관리자", "SUPER", passwordEncoder.encode(props.bootstrapPassword()));
        log.info("최고관리자 계정 '{}' 을 만들었습니다. 첫 로그인 때 OTP 등록과 비밀번호 변경이 필요합니다", props.bootstrapLoginId());
    }
}
