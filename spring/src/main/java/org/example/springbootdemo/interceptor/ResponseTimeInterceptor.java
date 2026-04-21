package org.example.springbootdemo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 响应时间监控拦截器
 * 记录每个请求的处理时间，用于性能监控
 * 开发时使用，生产环境可在WebConfig中注释掉注册代码
 */
@Slf4j
@Component
public class ResponseTimeInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求开始时间
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 计算耗时
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            
            // 只记录订单相关的请求
            if (uri.startsWith("/orders")) {
                // 所有订单请求都记录到日志文件（INFO级别）
                log.info("[性能监控] {} {} | 状态: {} | 耗时: {}ms", method, uri, status, duration);
                
                // 慢请求额外输出警告
                if (duration > 1000) {
                    log.warn("[慢请求告警] {} {} | 状态: {} | 耗时: {}ms ⚠️", method, uri, status, duration);
                }
            }
        }
    }
}
