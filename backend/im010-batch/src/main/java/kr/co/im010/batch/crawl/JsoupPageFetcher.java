package kr.co.im010.batch.crawl;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import kr.co.im010.batch.CrawlProperties;

/**
 * Jsoup 으로 페이지를 받는다. 같은 사이트 요청 사이에는 requestDelay(기본 3초) 이상 쉰다 (6.1).
 */
@Component
public class JsoupPageFetcher implements PageFetcher {

    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private final CrawlProperties props;
    private final Map<String, Long> lastRequestAt = new HashMap<>();

    public JsoupPageFetcher(CrawlProperties props) {
        this.props = props;
    }

    @Override
    public Document fetch(String url) throws IOException {
        waitTurn(URI.create(url).getHost());
        return Jsoup.connect(url)
                .userAgent(props.userAgent())
                .timeout((int) props.timeout().toMillis())
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(true)
                .get();
    }

    private synchronized void waitTurn(String host) throws IOException {
        long now = System.nanoTime();
        Long last = lastRequestAt.get(host);
        long waitNanos = last == null ? 0 : props.requestDelay().toNanos() - (now - last);
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
