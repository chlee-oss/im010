package kr.co.im010.core.row;

/** 이름별 건수 (접수 집계 등). */
public record CountRow(String key, String label, long count) {
}
