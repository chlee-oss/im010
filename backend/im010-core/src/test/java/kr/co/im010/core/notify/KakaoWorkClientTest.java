package kr.co.im010.core.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** 로컬 가짜 카카오워크 서버로 요청 모양 · 응답 처리 확인 */
class KakaoWorkClientTest {

    HttpServer server;
    List<String> calls = new CopyOnWriteArrayList<>();
    KakaoWorkClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = ex.getRequestURI().toString().substring("/v1/".length());
            calls.add(ex.getRequestMethod() + " " + path + " " + ex.getRequestHeaders().getFirst("Authorization") + " " + body);
            String res;
            int code = 200;
            if (path.startsWith("users.find_by_email")) {
                res = path.contains("nobody") ? "{\"success\":false,\"error\":{\"code\":\"user_not_found\",\"message\":\"없음\"}}"
                        : "{\"success\":true,\"user\":{\"id\":\"" + (path.contains("a%40") ? "11" : "22") + "\"}}";
                if (path.contains("nobody")) {
                    code = 400;
                }
            } else if (path.equals("conversations.open")) {
                res = "{\"success\":true,\"conversation\":{\"id\":\"777\",\"type\":\"group\"}}";
            } else {
                res = "{\"success\":true}";
            }
            byte[] out = res.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        client = new KakaoWorkClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void createsGroupRoomFromEmailsAndAllowsInvite() throws Exception {
        List<String> ids = client.findUserIds("KEY", List.of("a@x.co", "b@x.co"));
        String room = client.openGroup("KEY", ids, "im010서비스 알림");

        assertThat(ids).containsExactly("11", "22");
        assertThat(room).isEqualTo("777");
        assertThat(calls.get(0)).startsWith("GET users.find_by_email?email=a%40x.co Bearer KEY");
        assertThat(calls.get(2)).startsWith("POST conversations.open Bearer KEY")
                .contains("\"user_ids\":[\"11\",\"22\"]").contains("\"conversation_name\":\"im010서비스 알림\"");
        assertThat(calls.get(3)).startsWith("POST conversations/777/edit").contains("\"allow_invite\":true");
    }

    @Test
    void oneMemberOpensDirectRoom() throws Exception {
        client.openGroup("KEY", List.of("11"), "알림");

        assertThat(calls.get(0)).contains("\"user_id\":\"11\"").doesNotContain("user_ids");
        assertThat(calls).hasSize(1);
    }

    @Test
    void sendsMarkdownMessage() throws Exception {
        client.send("KEY", "777", KakaoWorkClient.text("WARN", "수집 실패 · 이상", "마블링 접속 실패", "3회 재시도"));

        assertThat(calls.get(0)).startsWith("POST messages.send Bearer KEY")
                .contains("\"conversation_id\":\"777\"").contains("\"is_markdown\":true")
                .contains("**⚠️ [im010 · 수집 실패 · 이상] 마블링 접속 실패**\\n3회 재시도");
    }

    @Test
    void unknownEmailNamesTheEmail() {
        assertThatThrownBy(() -> client.findUserIds("KEY", List.of("nobody@x.co")))
                .isInstanceOf(IOException.class).hasMessageContaining("nobody@x.co").hasMessageContaining("user_not_found");
    }

    @Test
    void missingKeyFailsWithoutCalling() {
        assertThatThrownBy(() -> client.send(" ", "777", "x")).isInstanceOf(IOException.class).hasMessageContaining("App Key");
        assertThat(calls).isEmpty();
    }
}
