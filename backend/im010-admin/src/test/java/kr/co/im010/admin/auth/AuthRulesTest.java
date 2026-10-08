package kr.co.im010.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AuthRulesTest {

    @Test
    void OTP_는_RFC_6238_시험값과_같다() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        // RFC 6238 부록 B (SHA1, 8자리 94287082 → 6자리 287082)
        assertThat(Totp.code(key, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(key, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
    }

    @Test
    void OTP_는_앞뒤_30초까지_받고_이미_쓴_번호는_다시_받지_않는다() {
        String secret = Totp.newSecret();
        byte[] key = Totp.unbase32(secret);
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);

        assertThat(Totp.verify(secret, Totp.code(key, step), now, null)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.code(key, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.code(key, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(secret, Totp.code(key, step), now, step)).isEmpty();   // 재사용
        assertThat(Totp.verify(secret, "12345", now, null)).isEmpty();
        assertThat(Totp.base32(Totp.unbase32(secret))).isEqualTo(secret);
        assertThat(Totp.uri("im010 admin", "admin", secret))
                .startsWith("otpauth://totp/im010%20admin:admin?secret=" + secret + "&issuer=im010%20admin");
    }

    @Test
    void 비밀번호_규칙() {
        assertThat(PasswordPolicy.check("Short1!", "admin")).contains("10자");
        assertThat(PasswordPolicy.check("onlylowercase", "admin")).contains("3종류");
        assertThat(PasswordPolicy.check("Admin-2026-pass", "admin")).contains("아이디");
        assertThat(PasswordPolicy.check("Im010-strong-pass", "admin")).isNull();
    }
}
