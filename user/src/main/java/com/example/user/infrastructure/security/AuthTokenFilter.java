package com.example.user.infrastructure.security;

import com.example.user.application.service.UserService;
import com.example.user.domain.entity.LoginTicket;
import com.example.user.domain.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

/**
 * 读取 Authorization: Bearer &lt;ticket&gt;，校验 Redis 中的登录凭证，
 * 成功则填充 SecurityContext。
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ticket = extractTicket(request);
        if (ticket != null) {
            LoginTicket lt = userService.getLoginTicket(ticket);
            if (isValid(lt)) {
                User user = userService.getById(lt.getUserId());
                if (user != null) {
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            user, null, userService.getAuthorities(user.getId()));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } else {
                log.debug("Invalid or expired ticket");
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String extractTicket(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean isValid(LoginTicket lt) {
        return lt != null && lt.getStatus() == 0 && lt.getExpired().after(new Date());
    }
}
