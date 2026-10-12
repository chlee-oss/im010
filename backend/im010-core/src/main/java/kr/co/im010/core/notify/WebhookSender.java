package kr.co.im010.core.notify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * 메신저 웹훅 발송. 메신저마다 받는 형식이 달라 종류별로 본문을 만든다.
 * <pre>
 * SLACK    Slack · Mattermost Incoming Webhook        {"text": "*제목*\n내용"}
 * TEAMS    Microsoft Teams Workflows(웹훅) 채널 게시   Adaptive Card 메시지
 * JANDI    잔디 커넥트 Incoming Webhook                {"body", "connectColor", "connectInfo"}
 * WEBHOOK  그 밖의 사내 메신저 · 중계 서버               {"type", "level", "title", "body", "sentAt"}
 * </pre>
 * https 주소만 받고 리다이렉트는 따라가지 않는다. 2xx 가 아니면 IOException.
 */
public class WebhookSender {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> LEVEL_COLOR = Map.of("INFO", "#1F2430", "WARN", "#FFB020", "URGENT", "#D9432F");

    private final HttpClient client;

    public WebhookSender() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void send(String kind, String url, String level, String typeLabel, String title, String body)
            throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme())) {
            throw new IOException("https 주소만 보낼 수 있습니다");
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8");
        if ("JANDI".equals(kind)) {
            req.header("Accept", "application/vnd.tosslab.jandi-v2+json");
        }
        HttpResponse<String> res = client.send(req.POST(HttpRequest.BodyPublishers.ofString(
                payload(kind, level, typeLabel, title, body))).build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            String b = res.body() == null ? "" : res.body();
            throw new IOException("HTTP " + res.statusCode() + (b.isBlank() ? "" : " " + b.substring(0, Math.min(200, b.length()))));
        }
    }

    /** 메신저 종류별 JSON 본문 */
    static String payload(String kind, String level, String typeLabel, String title, String body) {
        String prefix = switch (level) {
            case "URGENT" -> "🚨 ";
            case "WARN" -> "⚠️ ";
            default -> "";
        };
        String heading = prefix + "[im010 · " + typeLabel + "] " + title;
        String text = body == null ? "" : body;
        Object payload = switch (kind) {
            case "SLACK" -> Map.of("text", "*" + heading + "*" + (text.isEmpty() ? "" : "\n" + text));
            case "TEAMS" -> teams(heading, text);
            case "JANDI" -> Map.of("body", heading, "connectColor", LEVEL_COLOR.getOrDefault(level, "#1F2430"),
                    "connectInfo", List.of(Map.of("title", title, "description", text.isEmpty() ? "-" : text)));
            default -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", typeLabel);
                m.put("level", level);
                m.put("title", title);
                m.put("body", text);
                m.put("sentAt", OffsetDateTime.now().toString());
                yield m;
            }
        };
        return JSON.writeValueAsString(payload);
    }

    private static Map<String, Object> teams(String heading, String text) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", List.of(
                Map.of("type", "TextBlock", "text", heading, "weight", "Bolder", "wrap", true),
                Map.of("type", "TextBlock", "text", text.isEmpty() ? " " : text, "wrap", true)));
        return Map.of("type", "message", "attachments",
                List.of(Map.of("contentType", "application/vnd.microsoft.card.adaptive", "content", card)));
    }
}
