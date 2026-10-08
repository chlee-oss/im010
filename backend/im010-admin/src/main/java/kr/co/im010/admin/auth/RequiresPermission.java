package kr.co.im010.admin.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드에 필요한 권한: 프로그램(ST-01) × 동작(4.1). 붙어 있지 않은 /admin/api 메서드는 로그인만 확인한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    String program();

    Action action();
}
