package org.example.nowcoder.controller.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.entity.LoginTicket;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.service.MessageService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.CookieUtil;
import org.example.nowcoder.utils.HostHolder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;

/**
 * @author 23211
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginTicketInterceptor implements HandlerInterceptor {
    private final UserService userService;
    private final HostHolder hostHolder;
    private final MessageService messageService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取loginTicket
        String ticket = CookieUtil.getValue(request, "ticket");
        if(ticket!=null){
            // 获取LoginTicket
            LoginTicket loginTicket=userService.getLoginTicket(ticket);
            // 验证是否过期
            if(loginTicket!=null&&loginTicket.getStatus()==0&&loginTicket.getExpired().after(new Date())){
                // 查询当前用户
                User user = userService.findUserById(loginTicket.getUserId());
                // 存储到ThreadLocal
                hostHolder.setUser(user);
            }
        }
        return true;

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        User user=hostHolder.getUser();
        if(user!=null&&modelAndView!=null){
            int unreadLetterCount = messageService.findUnreadCount(user.getId(), null);
            int unreadNoticeCount = messageService.findNoticeUnreadCount(user.getId(), null);

            modelAndView.addObject("loginUser",user);
            modelAndView.addObject("messageUnread",unreadLetterCount+unreadNoticeCount);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        hostHolder.clear();
    }
}
