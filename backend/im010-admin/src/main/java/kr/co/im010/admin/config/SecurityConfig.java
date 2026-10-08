package kr.co.im010.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 백오피스 보안: 세션 쿠키 인증 · CSRF(쿠키 → X-XSRF-TOKEN 헤더) · 로그인 단계 API 만 공개.
 * 로그인 절차(비밀번호 → OTP → 비밀번호 변경)는 AuthService 가 처리하고, 끝나면 SecurityContext 를 세션에 저장한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 기본 계정(자동 생성 비밀번호)을 만들지 않게 빈 사용자 저장소를 둔다. 관리자 인증은 AuthService 가 한다. */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        http
                .securityContext(c -> c.securityContextRepository(contextRepository))
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/api/auth/**", "/actuator/health").permitAll()
                        .requestMatchers("/admin/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(l -> l.logoutUrl("/admin/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .requestCache(r -> r.disable());
        return http.build();
    }
}
