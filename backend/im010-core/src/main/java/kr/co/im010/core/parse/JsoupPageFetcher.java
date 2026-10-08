package kr.co.im010.core.parse;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Jsoup 으로 페이지를 받는다. 같은 사이트 요청 사이에는 requestDelay(기본 3초) 이상 쉰다 (백오피스 6.1).
 * 배치 수집과 백오피스 수집 URL [테스트]가 함께 쓴다.
 */
public class JsoupPageFetcher implements PageFetcher {

    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private final String userAgent;
    private final Duration requestDelay;
    private final Duration timeout;
    private final Map<String, Long> lastRequestAt = new HashMap<>();

    public JsoupPageFetcher(String userAgent, Duration requestDelay, Duration timeout) {
        this.userAgent = userAgent;
        this.requestDelay = requestDelay;
        this.timeout = timeout;
    }

    @Override
    public Document fetch(String url) throws IOException {
        waitTurn(URI.create(url).getHost());
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout((int) timeout.toMillis())
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(true)
                .get();
    }

    private synchronized void waitTurn(String host) throws IOException {
        long now = System.nanoTime();
        Long last = lastRequestAt.get(host);
        long waitNanos = last == null ? 0 : requestDelay.toNanos() - (now - last);
        if (waitNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        }
        lastRequestAt.put(host, System.nanoTime());
    }
}
