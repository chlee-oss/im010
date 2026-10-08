package kr.co.im010.core.row;

/** 백오피스 프로그램 (ST-01). actions 는 허용 동작을 '|' 로 이은 문자열. */
public record ProgramRow(
        String id,
        String menuGroup,
        String name,
        String path,
        int sortOrder,
        boolean enabled,
        String actions
) {
}
