package org.example.nowcoder.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.nowcoder.domain.entity.LoginTicket;
import org.example.nowcoder.domain.entity.User;
import org.example.nowcoder.application.dto.LoginResult;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Map;

/**
 * @author 23211
 */
public interface UserService extends IService<User> {
    Map<String , Object> register(User user);
    int activation(int userId, String code);
    LoginResult login(String username, String password, int expiredSeconds);

    void logout(String ticket);

    LoginTicket getLoginTicket(String ticket);

    int updateAvatar(int id, String avatarUrl);

    Map<String, Object> updatePassword(int id, String oldPassword, String newPassword);

    User findUserByName(String toName);

    Collection<? extends GrantedAuthority> getAuthorities(int id);
}
