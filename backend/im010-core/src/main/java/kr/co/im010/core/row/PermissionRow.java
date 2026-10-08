package kr.co.im010.core.row;

/** 권한 그룹의 프로그램별 동작 권한. actions 는 '|' 로 이은 문자열. */
public record PermissionRow(String programId, String actions) {
}
