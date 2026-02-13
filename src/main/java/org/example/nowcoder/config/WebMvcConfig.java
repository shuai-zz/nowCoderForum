package org.example.nowcoder.config;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.controller.interceptor.DataInterceptor;
import org.example.nowcoder.controller.interceptor.LoginRequiredInterceptor;
import org.example.nowcoder.controller.interceptor.LoginTicketInterceptor;
import org.example.nowcoder.controller.interceptor.MessageInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author 23211
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final LoginTicketInterceptor loginTicketInterceptor;
//    private final LoginRequiredInterceptor loginRequiredInterceptor;
    private final MessageInterceptor messageInterceptor;
    private final DataInterceptor dataInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginTicketInterceptor)
                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");

//        registry.addInterceptor(loginRequiredInterceptor)
//                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");
        registry.addInterceptor(messageInterceptor)
                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");
        registry.addInterceptor(dataInterceptor)
                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");
    }




}
