package com.example.user.infrastructure.event;

import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import com.example.shared.event.FollowEvent;
import com.example.shared.event.UnfollowEvent;
import com.example.user.infrastructure.mapper.UserStatisticsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatsEventListenerTest {

    @Mock
    private UserStatisticsMapper userStatisticsMapper;

    @InjectMocks
    private UserStatsEventListener listener;

    @Test
    void likedOnPostIncrementsReceivedLikeOfPostAuthor() {
        // entityUserId 是被点赞内容的作者 —— 这才是收赞的人
        when(userStatisticsMapper.incrementReceivedLikeCount(7, 1)).thenReturn(1);
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_POST, 99, 7));
        verify(userStatisticsMapper).incrementReceivedLikeCount(7, 1);
    }

    @Test
    void likedOnCommentAlsoIncrementsReceivedLike() {
        // 帖子 / 评论被赞同等对待，都计入 received_like_count
        when(userStatisticsMapper.incrementReceivedLikeCount(8, 1)).thenReturn(1);
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_COMMENT, 99, 8));
        verify(userStatisticsMapper).incrementReceivedLikeCount(8, 1);
    }

    @Test
    void unlikedDecrementsReceivedLike() {
        when(userStatisticsMapper.incrementReceivedLikeCount(7, -1)).thenReturn(1);
        listener.onUnliked(new EntityUnlikedEvent(1, ENTITY_TYPE_POST, 99, 7));
        verify(userStatisticsMapper).incrementReceivedLikeCount(7, -1);
    }

    @Test
    void likedDoesNotThrowOnZeroRows() {
        // 0 行命中应仅 warn 不抛 —— 上游 Redis 已写，最终一致性接受短暂偏差
        when(userStatisticsMapper.incrementReceivedLikeCount(7, 1)).thenReturn(0);
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_POST, 99, 7));
        // 不验证日志（不在测试范围）；只要无异常即可
        verify(userStatisticsMapper).incrementReceivedLikeCount(7, 1);
    }

    @Test
    void followOnUserIncrementsBothSides() {
        // userId 是发起者；entityUserId 是被关注者
        when(userStatisticsMapper.incrementFollowerCount(7, 1)).thenReturn(1);
        when(userStatisticsMapper.incrementFolloweeCount(1, 1)).thenReturn(1);
        listener.onFollowed(new FollowEvent(1, ENTITY_TYPE_USER, 7, 7));
        verify(userStatisticsMapper).incrementFollowerCount(7, 1);
        verify(userStatisticsMapper).incrementFolloweeCount(1, 1);
    }

    @Test
    void followOnNonUserIsIgnored() {
        // 当前业务只有"关注用户"，关注帖子等不应进 stats
        listener.onFollowed(new FollowEvent(1, ENTITY_TYPE_POST, 7, 7));
        verifyNoInteractions(userStatisticsMapper);
    }

    @Test
    void unfollowOnUserDecrementsBothSides() {
        when(userStatisticsMapper.incrementFollowerCount(7, -1)).thenReturn(1);
        when(userStatisticsMapper.incrementFolloweeCount(1, -1)).thenReturn(1);
        listener.onUnfollowed(new UnfollowEvent(1, ENTITY_TYPE_USER, 7, 7));
        verify(userStatisticsMapper).incrementFollowerCount(7, -1);
        verify(userStatisticsMapper).incrementFolloweeCount(1, -1);
    }

    @Test
    void unfollowOnNonUserIsIgnored() {
        listener.onUnfollowed(new UnfollowEvent(1, ENTITY_TYPE_COMMENT, 7, 7));
        verify(userStatisticsMapper, never()).incrementFollowerCount(anyInt(), anyInt());
        verify(userStatisticsMapper, never()).incrementFolloweeCount(anyInt(), anyInt());
    }

    @Test
    void followStillCallsFolloweeWhenFollowerReturnsZero() {
        // follower row 缺失不应短路 followee 的维护 —— 两次 UPDATE 是独立的
        when(userStatisticsMapper.incrementFollowerCount(7, 1)).thenReturn(0);
        when(userStatisticsMapper.incrementFolloweeCount(1, 1)).thenReturn(1);
        listener.onFollowed(new FollowEvent(1, ENTITY_TYPE_USER, 7, 7));
        verify(userStatisticsMapper).incrementFollowerCount(7, 1);
        verify(userStatisticsMapper).incrementFolloweeCount(1, 1);
    }
}
