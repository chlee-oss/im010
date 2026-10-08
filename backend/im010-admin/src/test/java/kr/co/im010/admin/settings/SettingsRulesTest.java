package kr.co.im010.admin.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import kr.co.im010.admin.auth.IpRules;
import kr.co.im010.admin.auth.PasswordPolicy;

class SettingsRulesTest {

    @Test
    void IP_허용_규칙() {
        IpRules rules = IpRules.parse("""
                # 본사
                203.0.113.0/24
                198.51.100.7
                2001:db8::/32
                """);

        assertThat(rules.allows("203.0.113.42")).isTrue();
        assertThat(rules.allows("203.0.114.1")).isFalse();
        assertThat(rules.allows("198.51.100.7")).isTrue();
        assertThat(rules.allows("198.51.100.8")).isFalse();
        assertThat(rules.allows("2001:db8:1::5")).isTrue();
        assertThat(rules.allows("2001:db9::1")).isFalse();
        assertThat(rules.allows("not-an-ip")).isFalse();
        assertThat(IpRules.parse("").isEmpty()).isTrue();
    }

    @Test
    void 잘못된_IP_규칙은_거절하고_호스트_이름은_조회하지_않는다() {
        assertThatThrownBy(() -> IpRules.parse("203.0.113.0/33")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpRules.parse("intranet.example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpRules.parse("10.0.0.0/abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 임시_비밀번호는_비밀번호_규칙을_지킨다() {
        for (int i = 0; i < 200; i++) {
            String p = AdminUserService.tempPassword("kim");
            assertThat(PasswordPolicy.check(p, "kim")).as(p).isNull();
            assertThat(p).hasSize(14);
        }
    }
}
