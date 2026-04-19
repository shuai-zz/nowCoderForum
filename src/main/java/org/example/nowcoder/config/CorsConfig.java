package org.example.nowcoder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置。
 * <p>注册为最高优先级的 Filter，先于 Spring Security 链处理 preflight。
 */
@Configuration
public class CorsConfig {

    /** 允许的前端来源，可通过配置覆盖 */
    @Value("${nowCoder.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        cfg.setAllowedHeaders(List.of(CorsConfiguration.ALL));
        cfg.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION, "X-Captcha-Owner"));
        cfg.setAllowCredentials(false); // 用 Bearer header 认证，无需 cookie
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(src));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}