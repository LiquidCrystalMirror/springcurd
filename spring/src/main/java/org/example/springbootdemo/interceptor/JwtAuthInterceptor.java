package org.example.springbootdemo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springbootdemo.dto.UserDTO;
import org.example.springbootdemo.util.JwtUtil;
import org.example.springbootdemo.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();


        // 从请求头获取 Token
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未提供有效的认证信息");
        }

        String token = authorization.substring(7);

        try {
            // 解析 Token 获取用户信息
            UserDTO user = jwtUtil.getUserFromToken(token);

            // 将用户信息存入请求属性中，Controller 可以直接使用
            request.setAttribute("currentUser", user);
            request.setAttribute("userId", user.getId());

            return true; // 放行

        } catch (Exception e) {
            throw new RuntimeException("Token 无效或已过期");
        }
    }
}
