package kr.co.im010.admin.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 단계 API (CM-01). 응답의 stage 로 화면이 다음 단계를 보여 준다. */
@RestController
@RequestMapping("/admin/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank @Size(max = 50) String loginId, @NotBlank @Size(max = 64) String password) {
    }

    public record OtpRequest(@NotBlank @Size(max = 10) String code) {
    }

    public record PasswordRequest(@Size(max = 64) String currentPassword, @NotBlank @Size(max = 64) String newPassword) {
    }

    public record StageResponse(AuthService.Stage stage) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/state")
    public StageResponse state(HttpServletRequest request) {
        return new StageResponse(authService.currentStage(request));
    }

    @PostMapping("/login")
    public StageResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return new StageResponse(authService.login(body.loginId(), body.password(), request));
    }

    @GetMapping("/otp-setup")
    public Map<String, String> otpSetup(HttpServletRequest request) {
        return authService.otpSetup(request.getSession(false));
    }

    @PostMapping("/otp")
    public StageResponse otp(@Valid @RequestBody OtpRequest body, HttpServletRequest request, HttpServletResponse response) {
        return new StageResponse(authService.verifyOtp(body.code(), request, response));
    }

    @PostMapping("/password")
    public StageResponse password(@Valid @RequestBody PasswordRequest body, HttpServletRequest request,
                                  HttpServletResponse response) {
        return new StageResponse(authService.changePassword(body.currentPassword(), body.newPassword(), request, response));
    }
}
