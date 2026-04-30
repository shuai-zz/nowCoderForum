package com.example.user.infrastructure.utils;

import com.example.shared.common.exception.AuthException;
import com.example.user.domain.User;
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
