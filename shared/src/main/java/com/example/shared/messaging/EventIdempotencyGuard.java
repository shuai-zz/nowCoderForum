package com.example.shared.messaging;

import com.example.shared.utils.RedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 消费者幂等守卫：以 Event.eventId 为 key 在 Redis 上 SETNX，已处理过的事件直接跳过。
 * <p>消费者订阅 at-least-once，重试 / rebalance 都会重复推送同一条消息；
 * 通知和索引这种"产生副作用"的处理必须在业务侧去重。
 * <p>TTL 24h：Kafka 默认重投递窗口远小于 24h，足够拦掉绝大多数重复；
 * 同时避免 Redis 无限堆积（一天的 UUID key 估算可控）。
 *
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIdempotencyGuard {

    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * @return 当前消费者是否应继续处理该事件。true=首次见到，应处理；false=已处理过，跳过。
     */
    public boolean tryAcquire(Event event) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.isBlank()) {
            // 旧消息没有 eventId，无法去重；保持兼容性放行（调用方需感知该事件可能被重复处理）
            return true;
        }
        String key = RedisKeyUtil.getProcessedEventKey(eventId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL.toSeconds(), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            log.info("Skipping duplicate event topic={} eventId={}", event.getTopic(), eventId);
            return false;
        }
        return true;
    }
}
