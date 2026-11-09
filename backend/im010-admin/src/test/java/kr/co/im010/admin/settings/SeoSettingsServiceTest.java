package kr.co.im010.admin.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import kr.co.im010.admin.settings.SeoSettingsService.SeoRequest;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.core.content.SiteMeta;

class SeoSettingsServiceTest {

    @Test
    void cleansWhitespaceAndKeepsBlankOg() {
        SiteMeta m = SeoSettingsService.validate(new SeoRequest("  im010\n 알뜰폰  ", "설명\t입니다", " ", null));

        assertThat(m.title()).isEqualTo("im010 알뜰폰");
        assertThat(m.description()).isEqualTo("설명 입니다");
        assertThat(m.ogTitle()).isEmpty();
        assertThat(m.effectiveOgTitle()).isEqualTo("im010 알뜰폰");
    }

    @Test
    void titleAndDescriptionAreRequired() {
        assertThatThrownBy(() -> SeoSettingsService.validate(new SeoRequest(" ", "설명", "", "")))
                .isInstanceOf(ApiException.class).hasMessageContaining("사이트 제목");
        assertThatThrownBy(() -> SeoSettingsService.validate(new SeoRequest("제목", null, "", "")))
                .isInstanceOf(ApiException.class).hasMessageContaining("사이트 설명");
    }

    @Test
    void lengthIsCountedInCharacters() {
        String eighty = "가".repeat(SiteMeta.DESCRIPTION_MAX);
        assertThat(SeoSettingsService.validate(new SeoRequest("제목", eighty, "", "")).description()).hasSize(80);

        assertThatThrownBy(() -> SeoSettingsService.validate(new SeoRequest("제목", eighty + "가", "", "")))
                .isInstanceOf(ApiException.class).hasMessageContaining("80자");
        assertThatThrownBy(() -> SeoSettingsService.validate(new SeoRequest("제목", "설명", "가".repeat(61), "")))
                .isInstanceOf(ApiException.class).hasMessageContaining("Open Graph 제목");
    }

    @Test
    void angleBracketsAreRejected() {
        assertThatThrownBy(() -> SeoSettingsService.validate(new SeoRequest("<script>", "설명", "", "")))
                .isInstanceOf(ApiException.class).hasMessageContaining("< >");
    }
}
