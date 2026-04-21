package org.example.springbootdemo.config;

import jakarta.annotation.Resource;
import org.example.springbootdemo.interceptor.JwtAuthInterceptor;
import org.example.springbootdemo.interceptor.ResponseTimeInterceptor;
import org.example.springbootdemo.interceptor.RoleInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private JwtAuthInterceptor jwtAuthInterceptor;
    @Resource
    private RoleInterceptor roleAuthInterceptor;
    @Resource
    private ResponseTimeInterceptor responseTimeInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")  // 拦截所有路径
                .excludePathPatterns(    // 排除白名单路径
                    "/users/login",
                    "/users/register",
                    "/doc.html",
                    "/v3/api-docs/**",     // OpenAPI JSON 数据
                    "/swagger-ui/**",      // Swagger UI 资源
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/v3/api-docs",
                    "/swagger-ui.html",
                    "/orders/**",
                    "/admin/stock/**"
                );
        registry.addInterceptor(roleAuthInterceptor)
                .addPathPatterns("/dashboard/**")  // 拦截所有路径
                .excludePathPatterns(    // 排除白名单路径
                        "/dashboard/getUsers"
                );
        
//        // 注册响应时间监控拦截器（放在最后，确保能监控所有请求）
//        registry.addInterceptor(responseTimeInterceptor)
//                .addPathPatterns("/**");
    }
}
