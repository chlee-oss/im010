package kr.co.im010.admin.auth;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.im010.core.mapper.SettingsMapper;

/**
 * 사내 IP 제한 (결정 #24 — 선택, 기본 OFF). 켜져 있으면 허용 목록 밖의 주소는 로그인 단계부터 막는다.
 * 설정은 30초마다 다시 읽는다 (ST-02 에서 바꾸면 곧 반영). nginx allow/deny 와 함께 써도 된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IpRestrictionFilter extends OncePerRequestFilter {

    public static final String ENABLED = "ip_restriction_enabled";
    public static final String ALLOWLIST = "ip_allowlist";
    private static final Duration CACHE = Duration.ofSeconds(30);

    private final SettingsMapper settingsMapper;
    private volatile Snapshot snapshot;

    private record Snapshot(boolean enabled, IpRules rules, Instant loadedAt) {
    }

    public IpRestrictionFilter(SettingsMapper settingsMapper) {
        this.settingsMapper = settingsMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Snapshot s = current();
        if (s.enabled() && !s.rules().allows(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"IP_BLOCKED\",\"detail\":\"허용되지 않은 접속 위치입니다\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 설정을 바꾼 직후 바로 반영 */
    public void refresh() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null || s.loadedAt().plus(CACHE).isBefore(Instant.now())) {
            boolean enabled = "true".equals(settingsMapper.findSetting(ENABLED));
            IpRules rules;
            try {
                rules = IpRules.parse(settingsMapper.findSetting(ALLOWLIST));
            } catch (IllegalArgumentException e) {
                rules = IpRules.parse("");   // 저장 때 검사하므로 오지 않지만, 잘못된 값이면 모두 막는다
            }
            s = new Snapshot(enabled, rules, Instant.now());
            snapshot = s;
        }
        return s;
    }
}
