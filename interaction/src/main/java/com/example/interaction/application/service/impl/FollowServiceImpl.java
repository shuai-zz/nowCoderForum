package com.example.interaction.application.service.impl;

import com.example.interaction.application.dto.FollowListItem;
import com.example.interaction.application.service.FollowService;
import com.example.shared.dto.AuthorRef;
import com.example.shared.event.FollowEvent;
import com.example.shared.event.UnfollowEvent;
import com.example.shared.utils.RedisKeyUtil;
import com.example.user.application.service.UserService;
import com.example.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 关注 Lua 脚本：原子执行 zadd(followee) + zadd(follower)
     * KEYS[1]=followeeKey, KEYS[2]=followerKey
     * ARGV[1]=entityId, ARGV[2]=userId, ARGV[3]=timestamp
     * 返回 zadd 新增数量（1 = 首次关注，0 = 已关注），用于幂等：仅首次发事件。
     */
    private static final String FOLLOW_LUA = """
            local added = redis.call('zadd', KEYS[1], ARGV[3], ARGV[1])
            redis.call('zadd', KEYS[2], ARGV[3], ARGV[2])
            return added
            """;

    /**
     * 取关 Lua 脚本：原子执行 zrem(followee) + zrem(follower)
     * KEYS[1]=followeeKey, KEYS[2]=followerKey
     * ARGV[1]=entityId, ARGV[2]=userId
     * 返回 zrem 删除数量（1 = 真实取关，0 = 本来就没关注），用于幂等：仅真实取关发事件。
     */
    private static final String UNFOLLOW_LUA = """
            local removed = redis.call('zrem', KEYS[1], ARGV[1])
            redis.call('zrem', KEYS[2], ARGV[2])
            return removed
            """;

    private static final DefaultRedisScript<Long> FOLLOW_SCRIPT =
            new DefaultRedisScript<>(FOLLOW_LUA, Long.class);
    private static final DefaultRedisScript<Long> UNFOLLOW_SCRIPT =
            new DefaultRedisScript<>(UNFOLLOW_LUA, Long.class);

    @Override
    public void follow(int userId, int entityType, int entityId, int entityUserId) {
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
        String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);
        // ARGV 走 RedisTemplate 的 valueSerializer（JSON），String.valueOf(123) 会被序列化成 "\"123\""
        // ZADD 收到带引号的 score 会抛 "not a valid float"。改传 Number，JSON 序列化为裸数字。
        Long added = redisTemplate.execute(FOLLOW_SCRIPT,
                List.of(followeeKey, followerKey),
                entityId, userId, System.currentTimeMillis());
        if (added != null && added == 1L) {
            eventPublisher.publishEvent(new FollowEvent(userId, entityType, entityId, entityUserId));
        }
    }

    @Override
    public void unfollow(int userId, int entityType, int entityId, int entityUserId) {
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
        String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);
        Long removed = redisTemplate.execute(UNFOLLOW_SCRIPT,
                List.of(followeeKey, followerKey),
                entityId, userId);
        if (removed != null && removed == 1L) {
            eventPublisher.publishEvent(new UnfollowEvent(userId, entityType, entityId, entityUserId));
        }
    }

    @Override
    public boolean hasFollowed(int userId, int entityType, int entityId) {
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
        return redisTemplate.opsForZSet().score(followeeKey, entityId) != null;
    }

    @Override
    public List<FollowListItem> findFollowees(int userId, int pageNum, int pageSize) {
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, ENTITY_TYPE_USER);
        return getUserFollowList(pageNum, pageSize, followeeKey);
    }

    @Override
    public List<FollowListItem> findFollowers(int userId, int pageNum, int pageSize) {
        String followerKey = RedisKeyUtil.getFollowerKey(ENTITY_TYPE_USER, userId);
        return getUserFollowList(pageNum, pageSize, followerKey);
    }

    private List<FollowListItem> getUserFollowList(int pageNum, int pageSize, String redisKey) {
        int start = (pageNum - 1) * pageSize;
        int end = start + pageSize - 1;

        Set<Object> targetIds = redisTemplate.opsForZSet().reverseRange(redisKey, start, end);

        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }

        // 批量查询用户信息，避免 N+1
        List<Integer> ids = targetIds.stream()
                .map(o -> (Integer) o)
                .toList();
        Map<Integer, User> userMap = userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, user->user));
        // 批量查询关注时间
        List<Object> results = redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            for (Integer id : ids) {
                connection.zSetCommands().zScore(redisKey.getBytes(), String.valueOf(id).getBytes());
            }
            return null;
        });
        // 构建ID与score映射
        Map<Integer, Double> scoreMap = new HashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Object o = results.get(i);
            Double score = (o == null ? null : ((Number) o).doubleValue());
            scoreMap.put(ids.get(i), score);
        }


        return targetIds.stream()
                .map(id -> {
                    User user = userMap.get((Integer) id);
                    if (user == null) {
                        // 用户已被删除：从关注列表里直接剔除（占位无意义，前端无法点进去）
                        return null;
                    }
                    AuthorRef auth = AuthorRef.of(user.getId(), user.getUsername(), user.getAvatarUrl());
                    Date followTime = Optional.ofNullable(scoreMap.get(id))
                            .map(s -> new Date(s.longValue()))
                            .orElse(null);
                    return new FollowListItem(auth, followTime);
                })
                .filter(Objects::nonNull)
                .toList();
    }

}