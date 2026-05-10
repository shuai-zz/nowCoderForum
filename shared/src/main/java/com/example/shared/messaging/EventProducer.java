package com.example.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 序列化 + 发送事件到 Kafka。
     * <ul>
     *   <li>自动给 Event 填入 UUID，作为消费侧幂等去重 key（at-least-once 下重复消费会被 SETNX 拦掉）。</li>
     *   <li>Event 是只含基础类型 + Map 的 POJO，序列化失败一定是编程错误。
     *       旧实现 catch 后只记日志，导致 caller 以为已成功投递、下游通知/索引静默丢失。
     *       这里抛 IllegalStateException，让事件丢失变成显式失败而非沉默 bug。</li>
     * </ul>
     */
    public void fireEvent(Event event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new IllegalStateException("Event serialization failed: " + event.getTopic(), e);
        }
        kafkaTemplate.send(event.getTopic(), payload);
    }
}
