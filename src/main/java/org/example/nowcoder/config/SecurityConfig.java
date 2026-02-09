package org.example.nowcoder.config;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authz -> authz
                        // 忽略静态资源
                        .requestMatchers("/resources/**").permitAll()
                        // 授权
                        .requestMatchers("/user/setting",
                                "/user/upload",
                                "/comment/add/**",
                                "/discuss/add",
                                "/letter/**",
                                "/notice/**",
                                "/like",
                                "/follow",
                                "/unfollow")
                        .hasAnyAuthority(AUTHORITY_USER, AUTHORITY_ADMIN, AUTHORITY_MODERATOR)
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
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