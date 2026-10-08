package kr.co.im010.core.row;

/** 접속 · 처리 이력 (ST-05). kind = LOGIN | ACCESS | ACTION */
public record AuditRow(
        Long adminId,
        String loginId,
        String kind,
        String programId,
        String action,
        String target,
        String detail,
        String ip
) {
}
