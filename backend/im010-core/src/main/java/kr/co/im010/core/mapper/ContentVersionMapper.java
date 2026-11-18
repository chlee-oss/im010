package kr.co.im010.core.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 고객 화면 미리 렌더링에 쓰이는 데이터의 지문 (운영 서버 im010-prerender 가 10분마다 비교해, 바뀌었을 때만 다시 만든다).
 * 제휴사 · 게시 요금제(게시 버전 · 판매 종료) · 이달의 요금제 · 인터넷 상품 · FAQ · 약관 · Footer · 알뜰AI 카테고리 ·
 * 고객에게 보이는 알뜰AI 글(예약 시각이 지나면 바뀜) · 검색엔진설정(seo_*) · 오늘 날짜(한국)를 묶은 md5.
 * 조회 수처럼 자꾸 바뀌지만 화면에 안 나오는 값은 넣지 않는다.
 */
@Mapper
public interface ContentVersionMapper {

    String contentVersion();
}
