package com.example.interaction.application.service.impl;

import com.example.interaction.application.service.EntityExistenceChecker;
import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import com.example.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EntityExistenceChecker entityExistenceChecker;

    @InjectMocks
    private LikeServiceImpl service;

    // ---------- like() ----------

    @Test
    void likeWhenLuaReturnsOnePublishesLikedEventAndReturnsOne() {
        // Lua 返回 1 = 新增点赞
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        int status = service.like(10, ENTITY_TYPE_POST, 99, 7);

        assertThat(status).isEqualTo(1);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(EntityLikedEvent.class);
        EntityLikedEvent e = (EntityLikedEvent) eventCaptor.getValue();
        assertThat(e.userId()).isEqualTo(10);
        assertThat(e.entityType()).isEqualTo(ENTITY_TYPE_POST);
        assertThat(e.entityId()).isEqualTo(99);
        assertThat(e.entityUserId()).isEqualTo(7);
    }

    @Test
    void likeWhenLuaReturnsZeroPublishesUnlikedEventAndReturnsZero() {
        // Lua 返回 0 = 取消点赞
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        int status = service.like(10, ENTITY_TYPE_COMMENT, 88, 7);

        assertThat(status).isZero();
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(EntityUnlikedEvent.class);
        EntityUnlikedEvent e = (EntityUnlikedEvent) eventCaptor.getValue();
        assertThat(e.entityType()).isEqualTo(ENTITY_TYPE_COMMENT);
        assertThat(e.entityId()).isEqualTo(88);
    }

    @Test
    void likeWhenLuaReturnsNullTreatsAsZeroAndStillPublishesUnliked() {
        // 防御：连接异常 / 脚本异常导致 null —— 取 0 而不是 NPE，发 Unliked 让读模型保持一致
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        int status = service.like(10, ENTITY_TYPE_POST, 99, 7);

        assertThat(status).isZero();
        verify(eventPublisher).publishEvent(any(EntityUnlikedEvent.class));
    }

    @Test
    void likeCallsExistenceCheckerBeforeRedis() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        service.like(10, ENTITY_TYPE_POST, 99, 7);

        verify(entityExistenceChecker).requireExists(ENTITY_TYPE_POST, 99);
    }

    @Test
    void likeShortCircuitsWhenEntityMissing() {
        // S2 加的存在性守卫：post 已软删 → 抛 ResourceNotFoundException，不应再写 Redis / 发事件
        doThrow(new ResourceNotFoundException("Post not found: 99"))
                .when(entityExistenceChecker).requireExists(ENTITY_TYPE_POST, 99);

        assertThatThrownBy(() -> service.like(10, ENTITY_TYPE_POST, 99, 7))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void likePassesCorrectKeysToLuaScript() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        service.like(10, ENTITY_TYPE_POST, 99, 7);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), eq("10"));
        List<String> keys = keysCaptor.getValue();
        // entityKey 命中点赞内容；userKey 累加在 entityUserId（"收赞数"）—— 不是点赞人 userId
        assertThat(keys).containsExactly(
                "like:entity:" + ENTITY_TYPE_POST + ":99",
                "like:user:7"
        );
    }

    // ---------- findEntityLikeStatus() ----------

    @Test
    void findEntityLikeStatusReturnsZeroForAnonymousUser() {
        // userId=0 = 未登录，直接短路 —— 既省 Redis 调用又防 SISMEMBER 把 "0" 当成有效用户
        int status = service.findEntityLikeStatus(0, ENTITY_TYPE_POST, 99);
        assertThat(status).isZero();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void findEntityLikeStatusReturnsOneWhenUserIsMember() {
        SetOperations<String, Object> setOps = mockSetOps();
        when(setOps.isMember("like:entity:1:99", 10)).thenReturn(true);

        assertThat(service.findEntityLikeStatus(10, ENTITY_TYPE_POST, 99)).isEqualTo(1);
    }

    @Test
    void findEntityLikeStatusReturnsZeroWhenUserIsNotMember() {
        SetOperations<String, Object> setOps = mockSetOps();
        when(setOps.isMember("like:entity:1:99", 10)).thenReturn(false);

        assertThat(service.findEntityLikeStatus(10, ENTITY_TYPE_POST, 99)).isZero();
    }

    @Test
    void findEntityLikeStatusReturnsZeroWhenIsMemberReturnsNull() {
        SetOperations<String, Object> setOps = mockSetOps();
        when(setOps.isMember("like:entity:1:99", 10)).thenReturn(null);

        assertThat(service.findEntityLikeStatus(10, ENTITY_TYPE_POST, 99)).isZero();
    }

    // ---------- findEntityLikeCount() ----------

    @Test
    void findEntityLikeCountReturnsSetCardinality() {
        SetOperations<String, Object> setOps = mockSetOps();
        when(setOps.size("like:entity:1:99")).thenReturn(42L);

        assertThat(service.findEntityLikeCount(ENTITY_TYPE_POST, 99)).isEqualTo(42L);
    }

    @Test
    void findEntityLikeCountReturnsZeroWhenKeyMissing() {
        SetOperations<String, Object> setOps = mockSetOps();
        when(setOps.size(any())).thenReturn(null);

        assertThat(service.findEntityLikeCount(ENTITY_TYPE_POST, 99)).isZero();
    }

    // ---------- findUserLikeCount() ----------

    @Test
    void findUserLikeCountReturnsZeroWhenCounterMissing() {
        ValueOperations<String, Object> valueOps = mockValueOps();
        when(valueOps.get("like:user:7")).thenReturn(null);

        assertThat(service.findUserLikeCount(7)).isZero();
    }

    @Test
    void findUserLikeCountUnwrapsNumberFromRedis() {
        ValueOperations<String, Object> valueOps = mockValueOps();
        // Redis 反序列化可能给到 Integer / Long 等 Number 子类
        when(valueOps.get("like:user:7")).thenReturn(123L);

        assertThat(service.findUserLikeCount(7)).isEqualTo(123);
    }

    // ---------- findEntityLikeStatuses() (anonymous shortcut) ----------

    @Test
    void findEntityLikeStatusesReturnsAllZerosForAnonymousUser() {
        var statuses = service.findEntityLikeStatuses(0, ENTITY_TYPE_POST, List.of(1, 2, 3));

        assertThat(statuses).containsOnly(
                java.util.Map.entry(1, 0),
                java.util.Map.entry(2, 0),
                java.util.Map.entry(3, 0)
        );
        // 重要：未登录用户不应触发 Redis pipeline
        verifyNoInteractions(redisTemplate);
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private SetOperations<String, Object> mockSetOps() {
        SetOperations<String, Object> setOps = (SetOperations<String, Object>) org.mockito.Mockito.mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        return setOps;
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> mockValueOps() {
        ValueOperations<String, Object> valueOps = (ValueOperations<String, Object>) org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return valueOps;
    }
}
