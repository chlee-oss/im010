package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 자주 묻는 질문 (ST-07 [FAQ]). */
public record FaqRow(
        long id,
        String category,
        String question,
        String answer,
        int sortOrder,
        boolean exposed,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
