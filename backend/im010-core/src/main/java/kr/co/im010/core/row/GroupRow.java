package kr.co.im010.core.row;

/** 권한 그룹 (ST-03). system = 최고관리자 그룹 */
public record GroupRow(long id, String code, String name, boolean system, int adminCount) {
}
