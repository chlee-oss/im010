package kr.co.im010.admin.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PrepaidDefaultsTest {

    @Test
    void 수집_배지_뒤에_선불_안면인증필수를_붙인다() {
        assertThat(ApprovalService.prepaidTags(List.of("5G", " 무제한"))).containsExactly("5G", "무제한", "선불", "안면인증필수");
    }

    @Test
    void 배지가_없으면_기본_태그만() {
        assertThat(ApprovalService.prepaidTags(List.of())).containsExactly("선불", "안면인증필수");
    }

    @Test
    void 중복은_빼고_6개를_넘으면_수집_배지를_줄인다() {
        assertThat(ApprovalService.prepaidTags(List.of("선불", "A", "A", "B", "C", "D", "E")))
                .containsExactly("A", "B", "C", "D", "선불", "안면인증필수");
    }

    @Test
    void 사용_기간_기본값은_30일() {
        assertThat(ApprovalService.PREPAID_DEFAULT_DAYS).isEqualTo(30);
    }
}
