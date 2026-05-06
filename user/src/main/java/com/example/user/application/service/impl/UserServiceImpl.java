package com.example.user.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.shared.exception.AuthException;
import com.example.shared.exception.ValidationException;
import com.example.shared.utils.ForumUtil;
import com.example.shared.utils.RedisKeyUtil;
import com.example.user.application.dto.LoginResult;
import com.example.user.application.service.UserService;
import com.example.user.domain.LoginTicket;
import com.example.user.domain.User;
import com.example.user.domain.entity.UserStatistics;
import com.example.user.infrastructure.mapper.UserMapper;
import com.example.user.infrastructure.mapper.UserStatisticsMapper;
import com.example.user.infrastructure.utils.MailClient;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.example.shared.constant.ForumConstant.*;


/**
 * @author 23211
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService, UserDetailsService {

    private final MailClient mailClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserStatisticsMapper userStatisticsMapper;

    public UserServiceImpl(MailClient mailClient,
                           RedisTemplate<String, Object> redisTemplate,
                           UserStatisticsMapper userStatisticsMapper) {
        this.mailClient = mailClient;
        this.redisTemplate = redisTemplate;
        this.userStatisticsMapper = userStatisticsMapper;
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
    @Transactional
    public void register(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Parameter cannot be null");
        }
        String username = user.getUsername();
        String password = user.getPassword();
        String email = user.getEmail();

        if (StringUtils.isBlank(username)) {
            throw new ValidationException("Username cannot be empty");
        }
        if (StringUtils.isBlank(password)) {
            throw new ValidationException("Password cannot be empty");
        }
        if (StringUtils.isBlank(email)) {
            throw new ValidationException("Email cannot be empty");
        }
        if (password.length() < 8) {
            throw new ValidationException("Password length must be greater than 8");
        }

        if (baseMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username))) {
            throw new ValidationException("This username already exists");
        }
        if (baseMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, email))) {
            throw new ValidationException("This email already exists");
        }

        user.setSalt(ForumUtil.generateUuid().substring(0, 5));
        user.setPassword(ForumUtil.md5(password + user.getSalt()));
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(ForumUtil.generateUuid());
        user.setAvatarUrl(String.format("https://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        baseMapper.insert(user);

        // 同步初始化统计读模型行：保证后续 EntityLikedEvent / FollowEvent
        // 触发的 UPDATE...WHERE user_id=? 一定能命中已有行。
        UserStatistics stats = new UserStatistics();
        stats.setUserId(user.getId());
        userStatisticsMapper.insert(stats);

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
    }

    @Override
    public void activation(int userId, String code) {
        User user = this.getById(userId);
        if (user.getStatus() == 1) {
            throw new ValidationException("Account already activated");
        }
        if (!Objects.equals(user.getActivationCode(), code)) {
            throw new ValidationException("Invalid activation code");
        }
        baseMapper.updateStatus(userId, 1);
        clearCache(userId);
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
        if(!Objects.equals(password, user.getPassword())){
            throw new AuthException("Password error");
        }

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
    public void updatePassword(int id, String oldPassword, String newPassword) {
        User user = baseMapper.selectById(id);
        oldPassword = ForumUtil.md5(oldPassword + user.getSalt());
        if (!oldPassword.equals(user.getPassword())) {
            throw new ValidationException("Incorrect Password");
        }
        if (newPassword.length() < 8) {
            throw new ValidationException("Password length must be greater than 8");
        }
        if (newPassword.equals(oldPassword)) {
            throw new ValidationException("New password cannot be the same as the old password");
        }
        try {
            baseMapper.updatePassword(id, ForumUtil.md5(newPassword + user.getSalt()));
        } catch (Exception e) {
            throw new ValidationException("Failed to update password");
        }
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
