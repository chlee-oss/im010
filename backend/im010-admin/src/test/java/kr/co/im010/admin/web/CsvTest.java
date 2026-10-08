package kr.co.im010.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void 쉼표_따옴표_줄바꿈은_감싼다() {
        assertThat(Csv.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(Csv.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(Csv.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(Csv.cell(null)).isEmpty();
    }

    @Test
    void 수식으로_해석될_수_있는_값은_앞에_따옴표를_붙인다() {
        assertThat(Csv.cell("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(Csv.cell("+82-10")).isEqualTo("'+82-10");
        assertThat(Csv.cell("@cmd")).isEqualTo("'@cmd");
        assertThat(Csv.cell(-3)).isEqualTo("-3");   // 숫자는 그대로
    }
}
