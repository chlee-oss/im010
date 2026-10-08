package kr.co.im010.core.parse;

import java.io.IOException;

import org.jsoup.nodes.Document;

/** 제휴사 페이지 받기. 테스트에서는 저장해 둔 HTML 로 바꿔 끼운다. */
public interface PageFetcher {

    Document fetch(String url) throws IOException;
}
