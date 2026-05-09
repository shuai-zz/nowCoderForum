package com.example.message.infrastructure.messaging;

import com.example.message.application.service.MessageService;
import com.example.message.domain.entity.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.example.shared.messaging.Event;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.shared.constant.ForumConstant.*;

/**
 * 系统通知事件消费者：处理评论、点赞、关注事件，生成系统通知消息。
 * 归属 message 模块（待实体迁移后迁入）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
    public void handleNotification(ConsumerRecord<String, String> record) {
        Event event = parseEvent(record);
        if (event == null) return;

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("userId", event.getUserId());
        content.put("entityType", event.getEntityType());
        content.put("entityId", event.getEntityId());
        if (event.getData() != null && !event.getData().isEmpty()) {
            content.putAll(event.getData());
        }
        String contentJson;
        try {
            contentJson = objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification content", e);
            return;
        }

        Message message = Message.builder()
                .fromId(SYSTEM_USER_ID)
                .toId(event.getEntityUserId())
                .conversationId(event.getTopic())
                .content(contentJson)
                .createTime(new Date())
                .build();
        messageService.addMessage(message);
    }

    private Event parseEvent(ConsumerRecord<String, String> record) {
        if (record == null || record.value() == null) {
            log.error("Kafka record value is null, topic={}", record == null ? "?" : record.topic());
            return null;
        }
        try {
            return objectMapper.readValue(record.value(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse event payload: {}", record.value(), e);
            return null;
        }
    }
}