package com.example.system.infrastructure.interceptor;

import com.example.system.application.service.DataService;
import com.example.user.domain.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author zhaoshuai
 */
@Component
public class DataInterceptor implements HandlerInterceptor {
    private final DataService dataService;

    public DataInterceptor(DataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录UV
        String ip = request.getRemoteHost();
        dataService.recordUv(ip);

        // 统计DAU
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            dataService.recordDau(user.getId());
        }
        return true;
    }
}
