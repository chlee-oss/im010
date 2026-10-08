package kr.co.im010.admin.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** @RequiresPermission 이 붙은 메서드는 프로그램 × 동작 권한을 확인한다 (403). */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
            if (required != null) {
                CurrentAdmin.require(required.program(), required.action());
            }
        }
        return true;
    }
}
