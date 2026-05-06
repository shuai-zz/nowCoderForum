package com.example.user.infrastructure.event;

import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import com.example.shared.event.FollowEvent;
import com.example.shared.event.UnfollowEvent;
import com.example.user.infrastructure.mapper.UserStatisticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;

@Component
@RequiredArgsConstructor
public class UserStatsEventListener {

    private final UserStatisticsMapper userStatisticsMapper;

    @EventListener
    public void onLiked(EntityLikedEvent event) {
        userStatisticsMapper.incrementReceivedLikeCount(event.entityUserId(), 1);
    }

    @EventListener
    public void onUnliked(EntityUnlikedEvent event) {
        userStatisticsMapper.incrementReceivedLikeCount(event.entityUserId(), -1);
    }

    @EventListener
    public void onFollowed(FollowEvent event) {
        if (event.entityType() == ENTITY_TYPE_USER) {
            userStatisticsMapper.incrementFollowerCount(event.entityUserId(), 1);
            userStatisticsMapper.incrementFolloweeCount(event.userId(), 1);
        }
    }

    @EventListener
    public void onUnfollowed(UnfollowEvent event) {
        if (event.entityType() == ENTITY_TYPE_USER) {
            userStatisticsMapper.incrementFollowerCount(event.entityUserId(), -1);
            userStatisticsMapper.incrementFolloweeCount(event.userId(), -1);
        }
    }
}
