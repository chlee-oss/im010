package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 관리자 계정 + 권한 그룹. groupSystem = 최고관리자 그룹(전체 권한). */
public record AdminUserRow(
        long id,
        String loginId,
        String name,
        long groupId,
        String groupCode,
        String groupName,
        boolean groupSystem,
        String status,
        String passwordHash,
        boolean mustChangePassword,
        String otpSecret,
        boolean otpEnabled,
        Long otpLastStep,
        int failedCount,
        OffsetDateTime lockedUntil
) {
}
