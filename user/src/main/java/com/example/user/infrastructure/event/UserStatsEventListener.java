package com.example.user.infrastructure.event;

import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import com.example.shared.event.FollowEvent;
import com.example.shared.event.UnfollowEvent;
import com.example.user.infrastructure.mapper.UserStatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserStatsEventListener {

    private final UserStatisticsMapper userStatisticsMapper;

    /**
     * 用户收到的总赞数统计：任何内容（帖子/评论）被赞都计入 received_like_count。
     */
    @EventListener
    public void onLiked(EntityLikedEvent event) {
        int rows = userStatisticsMapper.incrementReceivedLikeCount(event.entityUserId(), 1);
        warnIfMissing(rows, "received_like_count +1", event.entityUserId());
    }

    @EventListener
    public void onUnliked(EntityUnlikedEvent event) {
        int rows = userStatisticsMapper.incrementReceivedLikeCount(event.entityUserId(), -1);
        warnIfMissing(rows, "received_like_count -1", event.entityUserId());
    }

    @EventListener
    public void onFollowed(FollowEvent event) {
        if (event.entityType() == ENTITY_TYPE_USER) {
            warnIfMissing(userStatisticsMapper.incrementFollowerCount(event.entityUserId(), 1),
                    "follower_count +1", event.entityUserId());
            warnIfMissing(userStatisticsMapper.incrementFolloweeCount(event.userId(), 1),
                    "followee_count +1", event.userId());
        }
    }

    @EventListener
    public void onUnfollowed(UnfollowEvent event) {
        if (event.entityType() == ENTITY_TYPE_USER) {
            warnIfMissing(userStatisticsMapper.incrementFollowerCount(event.entityUserId(), -1),
                    "follower_count -1", event.entityUserId());
            warnIfMissing(userStatisticsMapper.incrementFolloweeCount(event.userId(), -1),
                    "followee_count -1", event.userId());
        }
    }

    /**
     * UserService.register 同事务 INSERT user_statistics 行，listener UPDATE 应该一定命中。
     * 0 行命中说明：注册流程缺漏 / 用户被强制硬删 / 数据被外部清理。
     * 不抛异常以避免回滚上游 Redis 操作（最终一致性允许临时不一致），仅 warn 让运维感知。
     */
    private static void warnIfMissing(int rows, String op, int userId) {
        if (rows == 0) {
            log.warn("user_statistics row missing for op={} userId={}", op, userId);
        }
    }
}
