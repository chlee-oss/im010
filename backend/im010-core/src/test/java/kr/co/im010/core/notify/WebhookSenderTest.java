package kr.co.im010.core.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class WebhookSenderTest {

    @Test
    void 메신저별_본문() {
        assertThat(WebhookSender.payload("SLACK", "URGENT", "수집 실패 · 이상", "수집 실패 — mv 후불", "접속 실패"))
                .isEqualTo("{\"text\":\"*🚨 [im010 · 수집 실패 · 이상] 수집 실패 — mv 후불*\\n접속 실패\"}");
        assertThat(WebhookSender.payload("TEAMS", "INFO", "테스트", "제목", "내용"))
                .contains("\"type\":\"message\"", "application/vnd.microsoft.card.adaptive", "\"AdaptiveCard\"", "[im010 · 테스트] 제목");
        assertThat(WebhookSender.payload("JANDI", "WARN", "판매 종료 처리", "판매 종료", "A, B"))
                .contains("\"connectColor\":\"#FFB020\"", "\"description\":\"A, B\"", "⚠️ [im010 · 판매 종료 처리] 판매 종료");
        assertThat(WebhookSender.payload("WEBHOOK", "INFO", "테스트", "제목", null))
                .contains("\"level\":\"INFO\"", "\"body\":\"\"", "\"sentAt\"");
    }

    @Test
    void https_주소만_보낸다() {
        assertThatThrownBy(() -> new WebhookSender().send("SLACK", "http://example.com/hook", "INFO", "t", "a", "b"))
                .isInstanceOf(IOException.class).hasMessageContaining("https");
    }
}
