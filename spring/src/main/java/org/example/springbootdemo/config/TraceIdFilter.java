package org.example.springbootdemo.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器 —— 为每个请求生成唯一 TraceId
 * <p>面试亮点：分布式链路追踪的基础设施，可在日志中串联整个请求链路</p>
 *
 * <ul>
 *   <li>每个请求生成 UUID 作为 TraceId</li>
 *   <li>写入 MDC，自动注入到所有日志输出</li>
 *   <li>写入响应头 X-Trace-Id，前端可获取用于问题反馈</li>
 *   <li>Ordered.HIGHEST_PRECEDENCE 保证最先执行</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 优先从请求头获取 TraceId（支持跨服务传递），否则新生成
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 写入 MDC，日志中可通过 %X{traceId} 输出
        MDC.put(TRACE_ID_MDC_KEY, traceId);

        // 写入响应头，方便前端排查问题
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理 MDC，防止内存泄漏
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
