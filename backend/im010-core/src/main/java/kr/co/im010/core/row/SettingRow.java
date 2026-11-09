package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** admin_setting 한 줄 (값 · 마지막 저장자 · 저장 일시) */
public record SettingRow(
        String key,
        String value,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
