package kr.co.im010.admin.auth;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * 로그인한 관리자 (세션에 저장). 권한은 로그인 때 읽어 두며, 권한관리에서 바꾸면 다음 로그인부터 적용된다.
 *
 * @param superAdmin  최고관리자 그룹 — 사용 중인 모든 프로그램의 모든 허용 동작
 * @param permissions 프로그램 ID → 동작
 */
public record AdminPrincipal(
        long id,
        String loginId,
        String name,
        String groupName,
        boolean superAdmin,
        Map<String, Set<Action>> permissions
) implements Serializable {

    public boolean can(String program, Action action) {
        Set<Action> actions = permissions.get(program);
        return actions != null && actions.contains(action);
    }
}
