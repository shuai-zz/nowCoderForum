package com.example.interaction.application.service.impl;

import com.example.interaction.application.service.EntityExistenceChecker;
import com.example.interaction.application.service.LikeService;
import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import com.example.shared.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 点赞服务实现类。
 *
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityExistenceChecker entityExistenceChecker;

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
    public int like(int userId, int entityType, int entityId, int entityUserId) {
        entityExistenceChecker.requireExists(entityType, entityId);
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);

        Long result = redisTemplate.execute(
                LIKE_SCRIPT,
                List.of(entityLikeKey, userLikeKey),
                String.valueOf(userId)
        );
        int likeStatus = result != null ? result.intValue() : 0;
        if (likeStatus == 1) {
            eventPublisher.publishEvent(new EntityLikedEvent(userId, entityType, entityId, entityUserId));
        } else {
            eventPublisher.publishEvent(new EntityUnlikedEvent(userId, entityType, entityId, entityUserId));
        }
        return likeStatus;
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
        if(userId==0){
            return 0;
        }
        Boolean isMember = redisTemplate.opsForSet().isMember(entityLikeKey, userId);
        return Boolean.TRUE.equals(isMember) ? 1 : 0;
    }

    @Override
    public int findUserLikeCount(int userId) {
        String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
        Number count = (Number) redisTemplate.opsForValue().get(userLikeKey);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Map<Integer, Long> findEntityLikeCounts(int entityType, List<Integer> entityIds) {
        List<String> keys = entityIds.stream()
                .map(id -> RedisKeyUtil.getEntityLikeKey(entityType, id))
                .toList();
        List<Object> results = redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            for (String key : keys) {
                connection.setCommands().sCard(key.getBytes());
            }
            return null;
        });

        Map<Integer, Long> map = new HashMap<>(entityIds.size());
        for (int i = 0; i < entityIds.size(); i++) {
            Object o = results.get(i);
            map.put(entityIds.get(i), o == null ? 0L : ((Number) o).longValue());
        }
        return map;

    }

    @Override
    public Map<Integer, Integer> findEntityLikeStatuses(int userId, int entityType, List<Integer> entityIds) {
        if(userId==0){
            return entityIds.stream().collect(Collectors.toMap(id->id, id->0));
        }
        String userIdStr = String.valueOf(userId);
        List<String> keys = entityIds.stream()
                .map(id -> RedisKeyUtil.getEntityLikeKey(entityType, id))
                .toList();
        List<Object> results = redisTemplate.executePipelined((RedisCallback<?>) connection -> {
            byte[] memberBytes = userIdStr.getBytes();
            for (String key : keys) {
                connection.setCommands().sIsMember(key.getBytes(), memberBytes);
            }
            return null;
        });
        Map<Integer, Integer> map = new HashMap<>(entityIds.size());
        for (int i = 0; i < entityIds.size(); i++) {
            Object o = results.get(i);
            map.put(entityIds.get(i), Boolean.TRUE.equals(o) ? 1 : 0);
        }
        return map;
    }
}