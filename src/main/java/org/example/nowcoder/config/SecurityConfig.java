package org.example.nowcoder.config;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.security.AuthTokenFilter;
import org.example.nowcoder.security.RestAccessDeniedHandler;
import org.example.nowcoder.security.RestAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.example.nowcoder.utils.ForumConstant.*;

/**
 * Spring Security 配置（P1 后：无状态 + Bearer Token 认证）。
 *
 * <p>认证流程：
 * <ol>
 *   <li>{@link AuthTokenFilter} 读取 {@code Authorization: Bearer &lt;ticket&gt;}</li>
 *   <li>查询 Redis 中的 LoginTicket</li>
 *   <li>有效则填充 SecurityContext + HostHolder</li>
 * </ol>
 *
 * <p>权限体系保留原有 user/admin/moderator 三级 Authority。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/resources/**");
    }

    /**
     * 阻止 Spring Boot 把 {@link AuthTokenFilter}（@Component 的 Filter）
     * 自动注册到 servlet filter chain。它只应作为 Security filter chain 的一环。
     */
    @Bean
    public FilterRegistrationBean<AuthTokenFilter> authTokenFilterRegistration(AuthTokenFilter filter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // API 文档 & 监控
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health", "/actuator/info"
                        ).permitAll()
                        // 新 REST 公开端点
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/activate/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/captcha"
                        ).permitAll()
                        // /api/v1/auth/{me,logout} 要求已登录
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/users/avatar/**").permitAll()
                        // Users REST（P2.3）
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/avatar")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/password")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        // Messages / Notices（P3.1）
                        .requestMatchers("/api/v1/messages/**", "/api/v1/notices/**")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        // Search（P3.2）：公开
                        .requestMatchers(HttpMethod.GET, "/api/v1/search").permitAll()
                        // Admin stats（P3.2）：仅管理员
                        .requestMatchers("/api/v1/admin/**").hasAuthority(AUTHORITY_ADMIN)
                        // Posts REST（P2.1）
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/posts",
                                "/api/v1/posts/*",
                                "/api/v1/posts/*/comments").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/posts/*/top",
                                "/api/v1/posts/*/wonderful").hasAuthority(AUTHORITY_MODERATOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/*")
                        .hasAuthority(AUTHORITY_ADMIN)
                        // Comments / Likes / Follows（P2.2）
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/users/*/followees",
                                "/api/v1/users/*/followers").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/comments",
                                "/api/v1/likes",
                                "/api/v1/follows")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/follows/**")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        // 老 Thymeleaf 兼容（过渡期保留，P5 删）
                        .requestMatchers(
                                "/login", "/register", "/activation/**",
                                "/kaptcha", "/forget", "/denied", "/error"
                        ).permitAll()
                        .requestMatchers("/user/avatar/**").permitAll()
                        // 需要登录的老路径（保留原有规则）
                        .requestMatchers(
                                "/user/setting", "/user/upload",
                                "/comment/add/**", "/discuss/add",
                                "/letter/**", "/notice/**",
                                "/like", "/follow", "/unfollow"
                        ).hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        .requestMatchers("/discuss/top", "/discuss/wonderful")
                        .hasAnyAuthority(AUTHORITY_MODERATOR)
                        .requestMatchers("/discuss/delete", "/data/**")
                        .hasAnyAuthority(AUTHORITY_ADMIN)
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // 禁用默认登出，防止覆盖业务登出
                .logout(logout -> logout.logoutUrl("/securityLogout"));

        return http.build();
    }
}
