package kr.co.im010.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class AiRulesTest {

    private static String type(int... bytes) {
        byte[] b = new byte[12];
        for (int i = 0; i < bytes.length; i++) {
            b[i] = (byte) bytes[i];
        }
        return UploadService.imageType(b, bytes.length);
    }

    @Test
    void 이미지_형식은_파일_내용으로_판단() {
        assertThat(type(0xFF, 0xD8, 0xFF, 0xE0)).isEqualTo("jpg");
        assertThat(type(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)).isEqualTo("png");
        assertThat(type('G', 'I', 'F', '8', '9', 'a')).isEqualTo("gif");
        assertThat(type('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')).isEqualTo("webp");
        byte[] svg = "<svg xmlns=\"".getBytes(StandardCharsets.UTF_8);
        assertThat(UploadService.imageType(svg, svg.length)).isNull();
        assertThat(type(0xFF, 0xD8)).isNull();   // 너무 짧음
    }

    @Test
    void 게시_예약은_미래_게시일의_게시글() {
        assertThat(AiPostAdminService.state("PUBLISHED", OffsetDateTime.now().plusDays(1))).isEqualTo("SCHEDULED");
        assertThat(AiPostAdminService.state("PUBLISHED", OffsetDateTime.now().minusMinutes(1))).isEqualTo("PUBLISHED");
        assertThat(AiPostAdminService.state("HIDDEN", OffsetDateTime.now().plusDays(1))).isEqualTo("HIDDEN");
        assertThat(AiPostAdminService.state("DRAFT", null)).isEqualTo("DRAFT");
    }

    @Test
    void 저장된_목록_값_읽기() {
        assertThat(AiPostAdminService.ids("3,7, 12")).containsExactly(3L, 7L, 12L);
        assertThat(AiPostAdminService.ids(null)).isEmpty();
        assertThat(AiPostAdminService.sources("[{\"name\":\"과기정통부\",\"title\":\"보도자료\",\"date\":\"2026.10.01\",\"url\":\"https://www.msit.go.kr\"}]"))
                .singleElement().satisfies(s -> assertThat(s.name()).isEqualTo("과기정통부"));
        assertThat(AiPostAdminService.sources("깨진 값")).isEmpty();
    }
}
