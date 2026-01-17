package org.example.nowcoder.service;

import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.LoginTicket;
import org.example.nowcoder.entity.User;

import java.util.List;
import java.util.Map;

/**
 * @author 23211
 */
public interface UserService {
    User findUserById(int id);
    Map<String , Object> register(User user);
    int activation(int userId, String code);
    Map<String, Object> login(String username, String password, int expiredSeconds);

    void logout(String ticket);

    LoginTicket getLoginTicket(String ticket);

    int updateAvatar(int id, String avatarUrl);

    Map<String, Object> updatePassword(int id, String oldPassword, String newPassword);

    User findUserByName(String toName);
}
