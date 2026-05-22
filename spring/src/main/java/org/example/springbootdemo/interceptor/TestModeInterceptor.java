package org.example.springbootdemo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.springbootdemo.config.TestModeContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 测试模式拦截器 - 检测请求头中的测试标记
 */
@Slf4j
@Component
public class TestModeInterceptor implements HandlerInterceptor {

    private static final String TEST_MODE_HEADER = "X-Test-Mode";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 检查请求头中是否包含测试模式标记
        String testMode = request.getHeader(TEST_MODE_HEADER);
        
        if ("true".equalsIgnoreCase(testMode)) {
            TestModeContext.setTestMode(true);
            log.debug("【测试模式】检测到测试请求: {} {}", request.getMethod(), request.getRequestURI());
        } else {
            TestModeContext.setTestMode(false);
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除 ThreadLocal，防止内存泄漏
        TestModeContext.clear();
    }
}
