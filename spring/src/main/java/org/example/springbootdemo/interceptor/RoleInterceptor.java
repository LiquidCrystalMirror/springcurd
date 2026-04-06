package org.example.springbootdemo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springbootdemo.dto.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();

        // 从请求属性中获取当前用户信息（由 JwtAuthInterceptor 设置）
        UserDTO currentUser = (UserDTO) request.getAttribute("currentUser");

        if (currentUser == null) {
            throw new RuntimeException("未登录或登录已过期");
        }

        Byte roleId = currentUser.getRoleId();

        // 根据路径判断需要的权限
        if (uri.contains("/updateUsers") || uri.contains("/delete")) {
            // 需要管理员权限（roleId = 1）
            if (roleId == null || roleId != 1) {
                throw new RuntimeException("权限不足，只有管理员可以执行此操作");
            }
        }

        return true;
    }
}
