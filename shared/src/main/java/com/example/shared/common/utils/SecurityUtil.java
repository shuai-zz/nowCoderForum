package com.example.shared.common.utils;

import com.example.shared.common.exception.AuthException;
import org.example.nowcoder.domain.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author zhaoshuai
 */
public class SecurityUtil {
    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!(auth.getPrincipal() instanceof User user)){
            throw new AuthException("用户未登录");
        }
        return user;
    }

}
