package org.example.nowcoder.config;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.LoginTicket;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.CookieUtil;
import org.example.nowcoder.utils.ForumUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.PrintWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import static org.example.nowcoder.utils.ForumConstant.*;

/**
 * @author zhaoshuai
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserService userService;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/resources/**");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 在权限判断前，把 ticket 对应的用户认证信息放进 SecurityContext
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    String ticket = CookieUtil.getValue(request, "ticket");
                    if (ticket != null) {
                        LoginTicket loginTicket = userService.getLoginTicket(ticket);
                        if (loginTicket != null && loginTicket.getStatus() == 0 && loginTicket.getExpired().after(new Date())) {
                            User user = userService.findUserById(loginTicket.getUserId());
                            Authentication authentication = new UsernamePasswordAuthenticationToken(
                                    user, user.getPassword(), userService.getAuthorities(user.getId())
                            );
                            SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
                        }
                    }
                }
                filterChain.doFilter(request, response);
            }
        }, UsernamePasswordAuthenticationFilter.class);

        // 添加自定义认证过滤器
        http.authorizeHttpRequests(authz -> authz
                        // 忽略静态资源
//                        .requestMatchers("/resources/**", "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg").permitAll()
                        // 授权
                        .requestMatchers("/user/setting",
                                "/user/upload",
                                "/comment/add/**",
                                "/discuss/add",
                                "/letter/**",
                                "/notice/**",
                                "/like",
                                "/follow",
                                "/unfollow"
                        )
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        .requestMatchers(
                                "/discuss/top",
                                "/discuss/wonderful"
                                )
                        .hasAnyAuthority(AUTHORITY_MODERATOR)
                        .requestMatchers(
                                "/discuss/delete",
                                "/data/**"
                        )
                        .hasAnyAuthority(AUTHORITY_ADMIN)
                        .anyRequest().permitAll()
        );
        http.exceptionHandling(exceptionHandling -> exceptionHandling
                        // 权限不足处理
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String xRequestedWith = request.getHeader("x-requested-with");
                            if (Objects.equals(xRequestedWith, "XMLHttpRequest")) {
                                response.setContentType("application/plain; charset=utf-8");
                                PrintWriter writer = response.getWriter();
                                writer.write(ForumUtil.getJsonString(403, "No permission"));
                            } else {
                                response.sendRedirect(request.getContextPath() + "/denied");
                            }
                        })
                        // 没有登录处理
                        .authenticationEntryPoint((request, response, authException) -> {
                            String xRequestedWith = request.getHeader("x-requested-with");
                            if (Objects.equals(xRequestedWith, "XMLHttpRequest")) {
                                response.setContentType("application/plain; charset=utf-8");
                                PrintWriter writer = response.getWriter();
                                writer.write(ForumUtil.getJsonString(403, "Not login, Please Login first"));
                            } else {
                                response.sendRedirect(request.getContextPath() + "/login");
                            }
                        })
                )
                //默认Logout退出，会用filter拦截
                //将security中的默认登出路径设置如下，避免覆盖我们自己的登出代码
                .logout(logout -> logout.logoutUrl("/securityLogout"))
                //Security获得权限需要在SecurityContext里获得


                // 禁用csrf保护（如果需要可以启用）
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
