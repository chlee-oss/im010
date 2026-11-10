package kr.co.im010.core.notify;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오워크 봇 API (https://docs.kakaoi.ai/kakao_work/webapireference/).
 * 봇은 사람이 만든 채팅방에는 보낼 수 없어, 멤버 이메일로 사용자를 찾아 봇이 그룹 채팅방을 만들고(openGroup) 그 방으로 보낸다.
 * <pre>
 * 인증        Authorization: Bearer {봇 App Key}
 * 사용자 찾기  GET  users.find_by_email?email=     (워크스페이스 가입 때 인증한 이메일만)
 * 채팅방 만들기 POST conversations.open {user_ids, conversation_name(60자)} — 한 명이면 1:1 방 {user_id}
 * 초대 허용    POST conversations/{id}/edit {allow_invite: true} — 멤버가 직접 다른 사람을 초대할 수 있게
 * 메시지      POST messages.send {conversation_id, text(10,000자), is_markdown}
 * 응답        {"success": true, ...} · 실패 {"success": false, "error": {"code", "message"}} · 분당 200회 넘으면 429
 * </pre>
 */
public class KakaoWorkClient {

    public static final String BASE_URL = "https://api.kakaowork.com/v1/";
    static final int TEXT_MAX = 10_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client;
    private final String baseUrl;

    public KakaoWorkClient() {
        this(BASE_URL);
    }

    KakaoWorkClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 이메일 → 카카오워크 사용자 ID. 하나라도 못 찾으면 어떤 이메일인지 알려 주며 실패 */
    public List<String> findUserIds(String appKey, List<String> emails) throws IOException, InterruptedException {
        List<String> ids = new ArrayList<>();
        for (String email : emails) {
            try {
                JsonNode res = call(appKey, "GET", "users.find_by_email?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8), null);
                ids.add(res.path("user").path("id").asString());
            } catch (IOException e) {
                throw new IOException(email + " 사용자를 찾지 못했습니다 (카카오워크 가입 때 인증한 이메일인지 확인): " + e.getMessage());
            }
        }
        return ids;
    }

    /** 봇이 채팅방을 만들고 ID 를 돌려준다. 그룹 방이면 멤버가 다른 사람을 초대할 수 있게 연다 */
    public String openGroup(String appKey, List<String> userIds, String name) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        if (userIds.size() == 1) {
            body.put("user_id", userIds.get(0));
        } else {
            body.put("user_ids", userIds);
            body.put("conversation_name", name.length() > 60 ? name.substring(0, 60) : name);
        }
        JsonNode res = call(appKey, "POST", "conversations.open", body);
        String id = res.path("conversation").path("id").asString();
        if (id.isEmpty()) {
            throw new IOException("채팅방 ID 를 받지 못했습니다");
        }
        if (userIds.size() > 1) {
            try {
                call(appKey, "POST", "conversations/" + id + "/edit", Map.of("allow_invite", true));
            } catch (IOException e) {
                // 초대 허용은 편의 기능 — 실패해도 방은 쓸 수 있다
            }
        }
        return id;
    }

    /** 채팅방에 마크다운 메시지 (10,000자 넘으면 자른다) */
    public void send(String appKey, String conversationId, String text) throws IOException, InterruptedException {
        String t = text.length() > TEXT_MAX ? text.substring(0, TEXT_MAX - 1) + "…" : text;
        call(appKey, "POST", "messages.send", Map.of("conversation_id", conversationId, "text", t, "is_markdown", true));
    }

    /** 알림 한 건을 카카오워크 마크다운 본문으로 */
    public static String text(String level, String typeLabel, String title, String body) {
        String prefix = switch (level) {
            case "URGENT" -> "🚨 ";
            case "WARN" -> "⚠️ ";
            default -> "";
        };
        String b = body == null ? "" : body;
        return "**" + prefix + "[im010 · " + typeLabel + "] " + title + "**" + (b.isEmpty() ? "" : "\n" + b);
    }

    private JsonNode call(String appKey, String method, String path, Object body) throws IOException, InterruptedException {
        if (appKey == null || appKey.isBlank()) {
            throw new IOException("봇 App Key 가 없습니다");
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + appKey);
        if ("POST".equals(method)) {
            req.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        } else {
            req.GET();
        }
        HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json;
        try {
            json = JSON.readTree(res.body() == null || res.body().isBlank() ? "{}" : res.body());
        } catch (RuntimeException e) {
            throw new IOException("HTTP " + res.statusCode() + " (JSON 아님)");
        }
        if (res.statusCode() / 100 != 2 || !json.path("success").asBoolean(false)) {
            JsonNode err = json.path("error");
            String code = err.path("code").asString("");
            String msg = err.path("message").asString("");
            throw new IOException("HTTP " + res.statusCode() + (code.isEmpty() ? "" : " " + code) + (msg.isEmpty() ? "" : " " + msg));
        }
        return json;
    }
}
