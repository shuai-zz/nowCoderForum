package org.example.nowcoder.controller.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.MessageService;
import org.example.nowcoder.utils.HostHolder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
public class MessageInterceptor implements HandlerInterceptor {
    private final HostHolder hostHolder;
    private final MessageService messageService;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        User user = hostHolder.getUser();
        if(user != null && modelAndView != null){
            int letterUnreadCount = messageService.findNoticeCount(user.getId(), null);
            int noticeUnreadCount = messageService.findNoticeUnreadCount(user.getId(), null);
            modelAndView.addObject("allUnreadCount", letterUnreadCount + noticeUnreadCount);
        }
    }
}
