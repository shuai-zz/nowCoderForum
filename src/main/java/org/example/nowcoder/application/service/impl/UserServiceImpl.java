package org.example.nowcoder.application.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.example.nowcoder.domain.entity.LoginTicket;
import org.example.nowcoder.domain.entity.User;
import org.example.nowcoder.application.dto.LoginResult;
import org.example.nowcoder.exception.AuthException;
import org.example.nowcoder.exception.ValidationException;
import org.example.nowcoder.infrastructure.mapper.UserMapper;
import org.example.nowcoder.application.service.UserService;
import org.example.nowcoder.infrastructure.util.ForumUtil;
import org.example.nowcoder.infrastructure.util.MailClient;
import org.example.nowcoder.infrastructure.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.example.nowcoder.infrastructure.util.ForumConstant.*;

/**
 * @author 23211
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService, UserDetailsService {

    private final MailClient mailClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserServiceImpl(MailClient mailClient, RedisTemplate<String, Object> redisTemplate) {
        this.mailClient = mailClient;
        this.redisTemplate = redisTemplate;
    }

    @Value("${nowcoder.path.frontend}")
    private String frontendDomain;

    @Override
    public User getById(Serializable id) {
        int userId = (int) id;
        User user = getCache(userId);
        if (user == null) {
            user = initCache(userId);
        }
        return user;
    }


    @Override
    public Map<String, Object> register(User user) {
        Map<String, Object> map = new HashMap<>();
        if (user == null) {
            throw new IllegalArgumentException("Parameter cannot be null");
        }
        if (StringUtils.isBlank(user.getUsername())) {
            map.put("usernameMsg", "Username cannot be empty");
            return map;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordMsg", "Password cannot be empty");
            return map;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailMsg", "Email cannot be empty");
            return map;
        }

        // Verify username
        User u = baseMapper.selectByName(user.getUsername());
        if (u != null) {
            map.put("usernameMsg", "This username already exists");
            return map;
        }

        //verify password
        if (user.getPassword().length() < 8) {
            map.put("passwordMsg", "Password length must be greater than 8");
            return map;
        }


        // Verify email
        u = baseMapper.selectByEmail(user.getEmail());
        if (u != null) {
            map.put("emailMsg", "This email already exists");
            return map;
        }

        // Register user
        user.setSalt(ForumUtil.generateUuid().substring(0, 5));
        user.setPassword(ForumUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(ForumUtil.generateUuid());
        user.setAvatarUrl(String.format("https://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        baseMapper.insert(user);

        //send activation email
        // 激活链接指向前端激活落地页，由前端调用 POST /api/v1/auth/activate
        String url = frontendDomain + "/activate/" + user.getId() + "/" + user.getActivationCode();
        String content = """
                <!doctype html>
                <html><body>
                <p>Hi, <b>%s</b></p>
                <p>You are registering for a new account on nowCoder Forum. Click
                <a href="%s">this link</a> to activate your account.</p>
                </body></html>
                """.formatted(user.getEmail(), url);
        mailClient.sendMail(user.getEmail(), "Activation", content);

        return map;
    }

    @Override
    public int activation(int userId, String code) {
        User user = baseMapper.selectById(userId);
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;
        } else if (user.getActivationCode().equals(code)) {
            baseMapper.updateStatus(userId, 1);
            clearCache(userId);
            return ACTIVATION_SUCCESS;
        } else {
            return ACTIVATION_FAILURE;
        }
    }

    @Override
    public LoginResult login(String username, String password, int expiredSeconds) {
        if (StringUtils.isBlank(username)) {
            throw new ValidationException("Username cannot be empty");
        }
        if (StringUtils.isBlank(password)) {
            throw new ValidationException("Password cannot be empty");
        }

        User user = baseMapper.selectByName(username);
        if (user == null) {
            throw new AuthException("This username does not exist");
        }
        if (user.getStatus() == 0) {
            throw new AuthException("This account has not been activated");
        }

        password = ForumUtil.md5(password + user.getSalt());
        if (!user.getPassword().equals(password)) {
            throw new AuthException("Password error");
        }

//        LoginTicket loginTicket = new LoginTicket();
//        loginTicket.setUserId(user.getId());
//        loginTicket.setTicket(ForumUtil.generateUuid());
//        loginTicket.setStatus(0);
//        loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000L));
        LoginTicket loginTicket=LoginTicket.builder()
                .userId(user.getId())
                .ticket(ForumUtil.generateUuid())
                .status(0)
                .expired(new Date(System.currentTimeMillis() + expiredSeconds * 1000L))
                .build();

        String redisKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
        redisTemplate.opsForValue().set(redisKey, loginTicket);

        return new LoginResult(loginTicket.getTicket(), user);
    }

    @Override
    public void logout(String ticket) {
//        loginTicketMapper.updateStatus(ticket, 1);
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
        if (loginTicket != null) {
            loginTicket.setStatus(1);
            redisTemplate.opsForValue().set(redisKey, loginTicket);
        }

    }


    @Override
    public LoginTicket getLoginTicket(String ticket) {
//        return loginTicketMapper.selectByTicket(ticket);
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(redisKey);
    }

    @Override
    public int updateAvatar(int id, String avatarUrl) {
//        return baseMapper.updateAvatar(id, avatarUrl);
        int rows = baseMapper.updateAvatar(id, avatarUrl);
        clearCache(id);
        return rows;
    }

    @Override
    public Map<String, Object> updatePassword(int id, String oldPassword, String newPassword) {
        HashMap<String, Object> map = new HashMap<>();
        User user = baseMapper.selectById(id);
        oldPassword = ForumUtil.md5(oldPassword + user.getSalt());
        if (!user.getPassword().equals(oldPassword)) {
            map.put("oldPasswordMsg", "Incorrect Password");
            return map;
        }
        if (newPassword.length() < 8) {
            map.put("newPasswordMsg", "Password length must be greater than 8");
            return map;
        }
        if (newPassword.equals(oldPassword)) {
            map.put("newPasswordMsg", "New password cannot be the same as the old password");
            return map;
        }
        try {
            baseMapper.updatePassword(id, ForumUtil.md5(newPassword + user.getSalt()));
        } catch (Exception e) {
            map.put("newPasswordMsg", "Failed to update password");
            return map;
        }
        return map;
    }

    @Override
    public User findUserByName(String name) {
        return baseMapper.selectByName(name);
    }

    // 1. 优先从缓存中取值
    private User getCache(int userId) {
        String redisKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(redisKey);
    }

    // 2. 如果缓存中没有，再从数据库取，并放入缓存
    private User initCache(int userId) {
        User user = baseMapper.selectById(userId);
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(redisKey, user, 3600, TimeUnit.SECONDS);
        return user;
    }

    // 3. 数据变更时，清除缓存
    private void clearCache(int userId) {
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(redisKey);
    }


    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        return this.findUserByName(username);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(int id) {
        User user = this.getById(id);

        List<GrantedAuthority> list = new ArrayList<>();
        list.add((GrantedAuthority) () ->
                switch (user.getType()) {
                    case 1 -> AUTHORITY_ADMIN;
                    case 2 -> AUTHORITY_MODERATOR;
                    default -> AUTHORITY_USER;
                });
        return list;
    }
}
