package com.example.user.application.service.impl;

import com.example.shared.exception.AuthException;
import com.example.shared.exception.ValidationException;
import com.example.shared.utils.ForumUtil;
import com.example.shared.utils.RedisKeyUtil;
import com.example.user.application.dto.LoginResult;
import com.example.user.application.service.UserService;
import com.example.user.domain.entity.LoginTicket;
import com.example.user.domain.entity.User;
import com.example.user.domain.entity.UserStatistics;
import com.example.user.infrastructure.mapper.UserMapper;
import com.example.user.infrastructure.mapper.UserStatisticsMapper;
import com.example.user.infrastructure.security.UserAuthorityResolver;
import com.example.user.infrastructure.security.UserDetailsAdapter;
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

import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * @author 23211
 */
@Service
public class UserServiceImpl
        implements UserService, UserDetailsService {

    private final MailClient mailClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserStatisticsMapper userStatisticsMapper;
    private final UserMapper userMapper;

    public UserServiceImpl(MailClient mailClient,
                           RedisTemplate<String, Object> redisTemplate,
                           UserStatisticsMapper userStatisticsMapper,
                           UserMapper userMapper) {
        this.mailClient = mailClient;
        this.redisTemplate = redisTemplate;
        this.userStatisticsMapper = userStatisticsMapper;
        this.userMapper = userMapper;
    }

    @Value("${nowcoder.path.frontend}")
    private String frontendDomain;

    @Override
    public User getById(int userId) {
        User user = getCache(userId);
        if (user == null) {
            user = initCache(userId);
        }
        return user;
    }


    @Override
    @Transactional
    public void register(User rawUser) {
        if (rawUser == null) {
            throw new IllegalArgumentException("Parameter cannot be null");
        }
        String username = rawUser.getUsername();
        String password = rawUser.getPassword();
        String email = rawUser.getEmail();

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

        if (userMapper.existsUsername(username)) {
            throw new ValidationException("This username already exists");
        }
        if (userMapper.existsEmail(email)) {
            throw new ValidationException("This email already exists");
        }

        String salt = ForumUtil.generateUuid().substring(0, 5);
        User user = User.builder()
                .username(username)
                .password(ForumUtil.md5(password + salt))
                .salt(salt)
                .email(email)
                .type(User.TYPE_USER)
                .status(User.STATUS_INACTIVE)
                .activationCode(ForumUtil.generateUuid())
                .avatarUrl(String.format("https://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)))
                .createTime(new Date())
                .build();
        userMapper.insert(user);

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

        if(!user.canActivateWith(code)){
            throw new ValidationException("Invalid activation code");
        }
        user.activate();
        userMapper.updateById(user);
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

        User user = userMapper.selectByName(username);
        if (user == null) {
            throw new AuthException("This username does not exist");
        }
        if (!user.isActivated()) {
            throw new AuthException("This account has not been activated");
        }

        password = ForumUtil.md5(password + user.getSalt());
        if (!Objects.equals(password, user.getPassword())) {
            throw new AuthException("Password error");
        }

        LoginTicket loginTicket = LoginTicket.builder()
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
        int rows = userMapper.updateAvatar(id, avatarUrl);
        clearCache(id);
        return rows;
    }

    @Override
    public void updatePassword(int id, String oldPassword, String newPassword) {
        User user = userMapper.selectById(id);
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
            userMapper.updatePassword(id, ForumUtil.md5(newPassword + user.getSalt()));
        } catch (Exception e) {
            throw new ValidationException("Failed to update password");
        }
    }

    @Override
    public User findUserByName(String name) {
        return userMapper.selectByName(name);
    }

    // 1. 优先从缓存中取值
    private User getCache(int userId) {
        String redisKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(redisKey);
    }

    // 2. 如果缓存中没有，再从数据库取，并放入缓存
    private User initCache(int userId) {
        User user = userMapper.selectById(userId);
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
        User user = this.findUserByName(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return new UserDetailsAdapter(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(int id) {
        User user = this.getById(id);
        return UserAuthorityResolver.resolve(user.getType());
    }

    @Override
    public UserStatistics getStatistics(int id) {
        return userStatisticsMapper.selectById(id);
    }

    @Override
    public List<User> listByIds(List<Integer> ids) {
        return userMapper.selectBatchIds(ids);
    }
}
