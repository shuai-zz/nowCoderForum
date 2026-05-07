package com.example.user.application.service;

import com.example.user.application.dto.LoginResult;
import com.example.user.domain.LoginTicket;
import com.example.user.domain.User;
import com.example.user.domain.entity.UserStatistics;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * @author 23211
 */
public interface UserService{
    User getById(int id);

    void register(User user);

    void activation(int userId, String code);

    LoginResult login(String username, String password, int expiredSeconds);

    void logout(String ticket);

    LoginTicket getLoginTicket(String ticket);

    int updateAvatar(int id, String avatarUrl);

    void updatePassword(int id, String oldPassword, String newPassword);

    User findUserByName(String toName);

    List<User> listByIds(List<Integer> ids);

    Collection<? extends GrantedAuthority> getAuthorities(int id);

    UserStatistics getStatistics(int id);
}
