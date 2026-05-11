package com.example.shared.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventIdempotencyGuardTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private EventIdempotencyGuard guard;

    @BeforeEach
    void wireRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void firstAcquireReturnsTrueAndSetsKeyWith24hTtl() {
        Event event = new Event().setTopic("publish").setEventId("uuid-1");
        when(valueOps.setIfAbsent(eq("event:processed:uuid-1"), eq("1"), eq(86400L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        assertThat(guard.tryAcquire(event)).isTrue();
        verify(valueOps).setIfAbsent("event:processed:uuid-1", "1", 86400L, TimeUnit.SECONDS);
    }

    @Test
    void duplicateAcquireReturnsFalse() {
        Event event = new Event().setTopic("publish").setEventId("uuid-2");
        when(valueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThat(guard.tryAcquire(event)).isFalse();
    }

    @Test
    void nullEventIdShortCircuitsToTrue() {
        // 旧消息未带 eventId —— 兼容性放行，但不写 Redis
        Event event = new Event().setTopic("publish").setEventId(null);
        // wireRedis @BeforeEach 的 stub 不会触发，因为短路前不会调用 opsForValue
        org.mockito.Mockito.reset(redisTemplate);
        assertThat(guard.tryAcquire(event)).isTrue();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void blankEventIdShortCircuitsToTrue() {
        Event event = new Event().setTopic("publish").setEventId("   ");
        org.mockito.Mockito.reset(redisTemplate);
        assertThat(guard.tryAcquire(event)).isTrue();
        verify(redisTemplate, never()).opsForValue();
    }
}
