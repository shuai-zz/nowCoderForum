package org.example.nowcoder.service.impl;

import jakarta.websocket.OnClose;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.service.FollowService;
import org.example.nowcoder.utils.RedisKeyUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {
    private final RedisTemplate<String, Object> redisTemplate;
    @Override
    public void follow(int userId, int entityType, int entityId) {

        // 正确使用 SessionCallback：只指定返回值泛型为 Object
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                // 拼接关注相关的 Redis Key
                String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
                String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);

                // 开启 Redis 事务
                operations.multi();

                // 1. 关注：给当前用户的关注列表添加目标实体（用时间戳作为分值，方便排序）
                operations.opsForZSet().add(followeeKey, entityId, System.currentTimeMillis());
                // 2. 关注：给目标实体的粉丝列表添加当前用户
                operations.opsForZSet().add(followerKey, userId, System.currentTimeMillis());

                // 执行事务并返回结果
                return operations.exec();

            }
        });
    }


    @Override
    public void unfollow(int userId, int entityType, int entityId) {
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
                String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);
                operations.multi();
                operations.opsForZSet().remove(followeeKey, entityId);
                operations.opsForZSet().remove(followerKey, userId);
                return operations.exec();
            }
        });
    }

    // 查询关注的实体的数量
    @Override
    public long findFolloweeCount(int userId, int entityType) {
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
        return redisTemplate.opsForZSet().zCard(followeeKey);
    }

    // 查询某实体的粉丝数量
    @Override
    public long findFollowerCount(int entityType, int entityId) {
        String followerKey = RedisKeyUtil.getFollowerKey(entityType, entityId);
        return redisTemplate.opsForZSet().zCard(followerKey);
    }

    // 查询当前用户是否关注了某个实体
    @Override
    public boolean hasFollowed(int userId, int entityType, int entityId) {
        String followeeKey = RedisKeyUtil.getFolloweeKey(userId, entityType);
        return redisTemplate.opsForZSet().score(followeeKey, entityId) != null;
    }
}
