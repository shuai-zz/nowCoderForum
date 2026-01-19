package org.example.nowcoder.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.utils.RedisKeyUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author 23211
 */
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {
    private final RedisTemplate<String,Object> redisTemplate;

    @Override
    public void like(int userId, int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        Boolean isMember = redisTemplate.opsForSet().isMember(entityLikeKey, userId);
        if (isMember) {
            // 取消点赞
            redisTemplate.opsForSet().remove(entityLikeKey, userId);
        } else {
            // 点赞
            redisTemplate.opsForSet().add(entityLikeKey, userId);
        }
    }

    //  查询某实体点赞的数量
    @Override
    public long findEntityLikeCount(int entityTypePost, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityTypePost, entityId);
        return redisTemplate.opsForSet().size(entityLikeKey);
    }

    //  查询某个用户对某实体的点赞状态
    @Override
    public int findEntityLikeStatus(int userId, int entityTypePost, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityTypePost, entityId);
        return redisTemplate.opsForSet().isMember(entityLikeKey, userId) ? 1 : 0;
    }

}
