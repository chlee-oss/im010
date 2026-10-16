package kr.co.im010.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostBodyTest {

    @Test
    void 마크다운을_HTML로_바꾸고_표와_이미지를_남긴다() {
        String html = PostBody.toHtml("""
                ## 1. 확인하기

                **eSIM** 지원 여부는 [제조사 안내](https://example.com/esim)에서.

                | 제휴사 | eSIM |
                |---|:-:|
                | 마블링 | ● |

                ![개통 화면](/uploads/ai/2026/10/a.png)
                """);
        assertThat(html).contains("<h2>1. 확인하기</h2>", "<strong>eSIM</strong>", "<table>", "<td align=\"center\">●</td>")
                .contains("href=\"https://example.com/esim\"", "target=\"_blank\"", "rel=\"noopener noreferrer nofollow\"")
                .contains("<img src=\"/uploads/ai/2026/10/a.png\" alt=\"개통 화면\" loading=\"lazy\">");
    }

    @Test
    void 스크립트_HTML과_위험한_링크는_남기지_않는다() {
        String html = PostBody.toHtml("""
                <script>alert(1)</script>
                <img src=x onerror=alert(1)>

                [클릭](javascript:alert(1))
                """);
        assertThat(html).doesNotContain("<script", "<img", "javascript:")
                .contains("&lt;script&gt;", "&lt;img src=x");   // 원문 HTML 은 실행되지 않고 글자로 보인다
        assertThat(html).contains("<a>클릭</a>");                  // 위험한 링크 주소는 빠진다
    }

    @Test
    void 읽는_시간과_주소용_이름() {
        assertThat(PostBody.readMinutes("<p>" + "가".repeat(1200) + "</p>")).isEqualTo(3);
        assertThat(PostBody.readMinutes("")).isEqualTo(1);
        assertThat(PostBody.slugify("eSIM으로 알뜰폰 개통하는 법, 10분이면 끝!")).isEqualTo("esim으로-알뜰폰-개통하는-법-10분이면-끝");
        assertThat(PostBody.slugify("!!!")).isEqualTo("post");
    }
}
