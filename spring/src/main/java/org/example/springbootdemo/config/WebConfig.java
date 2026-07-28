package org.example.springbootdemo.config;

import jakarta.annotation.Resource;
import org.example.springbootdemo.interceptor.JwtAuthInterceptor;
import org.example.springbootdemo.interceptor.ResponseTimeInterceptor;
import org.example.springbootdemo.interceptor.RoleInterceptor;
import org.example.springbootdemo.interceptor.StatusInterceptor;
import org.example.springbootdemo.interceptor.TestModeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private JwtAuthInterceptor jwtAuthInterceptor;
    @Resource
    private StatusInterceptor statusInterceptor;
    @Resource
    private RoleInterceptor roleAuthInterceptor;
    @Resource
    private ResponseTimeInterceptor responseTimeInterceptor;
    @Resource
    private TestModeInterceptor testModeInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册测试模式拦截器（放在最前面，优先执行）
        registry.addInterceptor(testModeInterceptor)
                .addPathPatterns("/**");
        
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/auth/login",
                    "/auth/register",
                    "/doc.html",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/v3/api-docs",
                    "/swagger-ui.html"
                );

        // 状态拦截器：检查用户是否启用/审核通过（对所有需认证的路径生效）
        registry.addInterceptor(statusInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/auth/login",
                    "/auth/register",
                    "/doc.html",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/v3/api-docs",
                    "/swagger-ui.html"
                );

        registry.addInterceptor(roleAuthInterceptor)
                .addPathPatterns("/dashboard/**", "/replenish/**", "/staff/**")
                .excludePathPatterns(
                    "/dashboard/orders",
                    "/dashboard/stocks"
                );
        
//        // 注册响应时间监控拦截器（放在最后，确保能监控所有请求）
//        registry.addInterceptor(responseTimeInterceptor)
//                .addPathPatterns("/**");
    }
}
