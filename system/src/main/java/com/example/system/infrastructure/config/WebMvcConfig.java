package com.example.system.infrastructure.config;

import com.example.system.infrastructure.interceptor.DataInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author zhaoshuai
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final DataInterceptor dataInterceptor;

    public WebMvcConfig(DataInterceptor dataInterceptor) {
        this.dataInterceptor = dataInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dataInterceptor)
                .excludePathPatterns(
                        // 静态资源
                        "/**/*.css",
                        "/**/*.js",
                        "/**/*.png",
                        "/**/*.jpg",
                        "/**/*.jpeg",
                        "/**/*.gif",
                        "/**/*.svg",
                        "/**/*.ico",
                        "/**/*.woff",
                        "/**/*.woff2",
                        "/**/*.ttf",
                        "/**/*.eot",
                        // Swagger UI 相关
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        // Actuator 健康检查
                        "/actuator/**",
                        // 错误页面
                        "/error"
                );
    }
}
