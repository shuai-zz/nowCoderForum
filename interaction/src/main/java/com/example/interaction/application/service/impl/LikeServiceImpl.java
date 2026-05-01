package com.example.interaction.application.service.impl;

import com.example.interaction.application.service.LikeService;
import com.example.shared.common.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 点赞服务实现类。
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 原子性点赞/取消点赞 Lua 脚本。
     * 返回 1 表示点赞，返回 0 表示取消点赞。
     */
    private static final String LIKE_TOGGLE_LUA = """
            local entityKey = KEYS[1]
            local userKey = KEYS[2]
            local userId = ARGV[1]
            if redis.call('sismember', entityKey, userId) == 1 then
                redis.call('srem', entityKey, userId)
                redis.call('decr', userKey)
                return 0
            else
                redis.call('sadd', entityKey, userId)
                redis.call('incr', userKey)
                return 1
            end
            """;

    private static final DefaultRedisScript<Long> LIKE_SCRIPT =
            new DefaultRedisScript<>(LIKE_TOGGLE_LUA, Long.class);

    @Override
    public void like(int userId, int entityType, int entityId, int entityUserId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);

        redisTemplate.execute(
                LIKE_SCRIPT,
                List.of(entityLikeKey, userLikeKey),
                String.valueOf(userId)
        );
    }

    @Override
    public long findEntityLikeCount(int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        Long count = redisTemplate.opsForSet().size(entityLikeKey);
        return count == null ? 0 : count;
    }

    @Override
    public int findEntityLikeStatus(int userId, int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        Boolean isMember = redisTemplate.opsForSet().isMember(entityLikeKey, userId);
        return Boolean.TRUE.equals(isMember) ? 1 : 0;
    }

    @Override
    public int findUserLikeCount(int userId) {
        String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
        Number count = (Number) redisTemplate.opsForValue().get(userLikeKey);
        return count == null ? 0 : count.intValue();
    }

}