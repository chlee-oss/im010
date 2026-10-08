package kr.co.im010.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.AdminPrincipal;
import kr.co.im010.admin.config.SecurityConfig;
import kr.co.im010.admin.review.ReviewController;
import kr.co.im010.admin.review.ReviewService;
import kr.co.im010.core.mapper.SettingsMapper;

/** 로그인 필요(401) · 프로그램 × 동작 권한(403) · CSRF 확인 */
@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class SecuritySliceTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReviewService reviewService;

    @MockitoBean
    SettingsMapper settingsMapper;   // IpRestrictionFilter (설정 없음 = IP 제한 꺼짐)

    private static UsernamePasswordAuthenticationToken admin(Map<String, Set<Action>> perms) {
        AdminPrincipal p = new AdminPrincipal(1, "kim", "김운영", "콘텐츠 운영자", false, perms);
        return UsernamePasswordAuthenticationToken.authenticated(p, null, List.of());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/admin/api/batch-items")).andExpect(status().isUnauthorized());
    }

    @Test
    void 프로그램_권한이_없으면_403() throws Exception {
        mvc.perform(get("/admin/api/batch-items").with(authentication(admin(Map.of("RC-01", EnumSet.of(Action.VIEW))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 조회_권한이_있으면_목록() throws Exception {
        given(reviewService.list(any(), any(), any(), any(), anyInt()))
                .willReturn(new ReviewService.Page(LocalDate.of(2026, 10, 8), Map.of(), 0, 1, List.of()));
        mvc.perform(get("/admin/api/batch-items").with(authentication(admin(Map.of("BA-02", EnumSet.of(Action.VIEW))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedOn").value("2026-10-08"));
    }

    @Test
    void 점검은_점검_권한과_CSRF_토큰이_있어야_한다() throws Exception {
        given(reviewService.review(anyList())).willReturn(new ReviewService.BulkResult(List.of(5L), List.of()));
        String body = "{\"ids\":[5]}";
        var viewOnly = admin(Map.of("BA-02", EnumSet.of(Action.VIEW)));
        var reviewer = admin(Map.of("BA-02", EnumSet.of(Action.VIEW, Action.REVIEW)));

        mvc.perform(post("/admin/api/batch-items/review").with(authentication(reviewer))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());   // CSRF 토큰 없음
        mvc.perform(post("/admin/api/batch-items/review").with(authentication(viewOnly)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());   // 점검 권한 없음
        mvc.perform(post("/admin/api/batch-items/review").with(authentication(reviewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done[0]").value(5));
    }
}
