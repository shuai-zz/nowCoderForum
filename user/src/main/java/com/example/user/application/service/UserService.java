package com.example.user.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.user.application.dto.LoginResult;
import com.example.user.domain.LoginTicket;
import com.example.user.domain.User;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Map;

/**
 * @author 23211
 */
public interface UserService extends IService<User> {
    void register(User user);
    int activation(int userId, String code);
    LoginResult login(String username, String password, int expiredSeconds);

    void logout(String ticket);

    LoginTicket getLoginTicket(String ticket);

    int updateAvatar(int id, String avatarUrl);

    Map<String, Object> updatePassword(int id, String oldPassword, String newPassword);

    User findUserByName(String toName);

    Collection<? extends GrantedAuthority> getAuthorities(int id);
}
