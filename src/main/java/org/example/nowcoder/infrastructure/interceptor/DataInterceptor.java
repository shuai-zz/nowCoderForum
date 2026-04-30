package org.example.nowcoder.infrastructure.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.domain.entity.User;
import org.example.nowcoder.application.service.DataService;
import org.example.nowcoder.infrastructure.util.HostHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author zhaoshuai
 */
@Component
public class DataInterceptor implements HandlerInterceptor {
    private final DataService dataService;
    private final HostHolder hostHolder;

    public DataInterceptor(DataService dataService, HostHolder hostHolder) {
        this.dataService = dataService;
        this.hostHolder = hostHolder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录UV
        String ip = request.getRemoteHost();
        dataService.recordUv(ip);

        // 统计DAU
        User user = hostHolder.getUser();
        if (user != null){
            dataService.recordDau(user.getId());
        }
        return true;
    }
}
