package com.example.interaction.application.service.impl;

import com.example.shared.event.FollowEvent;
import com.example.shared.event.UnfollowEvent;
import com.example.user.application.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FollowServiceImpl service;

    // ---------- follow() ----------

    @Test
    void followWhenLuaReturnsOnePublishesFollowEvent() {
        // Lua 返回 1 = ZADD 真实新增
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);

        service.follow(10, ENTITY_TYPE_USER, 7, 7);

        ArgumentCaptor<FollowEvent> captor = ArgumentCaptor.forClass(FollowEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FollowEvent e = captor.getValue();
        assertThat(e.userId()).isEqualTo(10);
        assertThat(e.entityType()).isEqualTo(ENTITY_TYPE_USER);
        assertThat(e.entityId()).isEqualTo(7);
        assertThat(e.entityUserId()).isEqualTo(7);
    }

    @Test
    void followWhenLuaReturnsZeroDoesNotPublishEvent() {
        // 幂等：重复关注 ZADD 返回 0，不重复发事件 —— 避免读模型 follower_count 多累加
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);

        service.follow(10, ENTITY_TYPE_USER, 7, 7);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void followWhenLuaReturnsNullDoesNotPublishEvent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(null);

        service.follow(10, ENTITY_TYPE_USER, 7, 7);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void followPassesCorrectKeysAndArgsToLuaScript() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);

        long before = System.currentTimeMillis();
        service.follow(10, ENTITY_TYPE_USER, 7, 7);
        long after = System.currentTimeMillis();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> entityIdArg = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> userIdArg = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> tsArg = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(),
                entityIdArg.capture(), userIdArg.capture(), tsArg.capture());

        assertThat(keysCaptor.getValue()).containsExactly(
                "followee:10:" + ENTITY_TYPE_USER,
                "follower:" + ENTITY_TYPE_USER + ":7"
        );
        // 关键：JSON 序列化时必须是 Number 而非 String —— ZADD 的 score 不接受带引号字符串
        assertThat(entityIdArg.getValue()).isInstanceOf(Integer.class).isEqualTo(7);
        assertThat(userIdArg.getValue()).isInstanceOf(Integer.class).isEqualTo(10);
        assertThat(tsArg.getValue()).isInstanceOf(Long.class);
        long ts = (Long) tsArg.getValue();
        assertThat(ts).isBetween(before, after);
    }

    // ---------- unfollow() ----------

    @Test
    void unfollowWhenLuaReturnsOnePublishesUnfollowEvent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        service.unfollow(10, ENTITY_TYPE_USER, 7, 7);

        ArgumentCaptor<UnfollowEvent> captor = ArgumentCaptor.forClass(UnfollowEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        UnfollowEvent e = captor.getValue();
        assertThat(e.userId()).isEqualTo(10);
        assertThat(e.entityId()).isEqualTo(7);
    }

    @Test
    void unfollowWhenLuaReturnsZeroDoesNotPublishEvent() {
        // 幂等：没关注过就取关 → 不发事件 —— 防 follower_count 被错误减成负数
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        service.unfollow(10, ENTITY_TYPE_USER, 7, 7);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unfollowWhenLuaReturnsNullDoesNotPublishEvent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(null);

        service.unfollow(10, ENTITY_TYPE_USER, 7, 7);

        verifyNoInteractions(eventPublisher);
    }

    // ---------- hasFollowed() ----------

    @Test
    void hasFollowedReturnsTrueWhenScorePresent() {
        ZSetOperations<String, Object> zset = mockZSetOps();
        when(zset.score("followee:10:" + ENTITY_TYPE_USER, 7)).thenReturn(1234567890.0);

        assertThat(service.hasFollowed(10, ENTITY_TYPE_USER, 7)).isTrue();
    }

    @Test
    void hasFollowedReturnsFalseWhenScoreNull() {
        ZSetOperations<String, Object> zset = mockZSetOps();
        when(zset.score(any(), eq(7))).thenReturn(null);

        assertThat(service.hasFollowed(10, ENTITY_TYPE_USER, 7)).isFalse();
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private ZSetOperations<String, Object> mockZSetOps() {
        ZSetOperations<String, Object> zset = (ZSetOperations<String, Object>) org.mockito.Mockito.mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zset);
        return zset;
    }
}
