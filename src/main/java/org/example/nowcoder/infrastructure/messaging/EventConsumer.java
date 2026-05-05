package org.example.nowcoder.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.nowcoder.domain.entity.DiscussPost;
import com.example.shared.messaging.Event;
import org.example.nowcoder.domain.entity.Message;
import org.example.nowcoder.application.service.DiscussPostService;
import org.example.nowcoder.application.service.ElasticSearchService;
import org.example.nowcoder.application.service.MessageService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.example.nowcoder.infrastructure.util.ForumConstant.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    private final MessageService messageService;
    private final DiscussPostService discussPostService;
    private final ElasticSearchService elasticSearchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
    public void handleCommentMessage(ConsumerRecord<String, String> record) {
        Event event = parseEvent(record);
        if (event == null) return;

        Message message = new Message();
        message.setFromId(SYSTEM_USER_ID);
        message.setToId(event.getEntityUserId());
        message.setConversationId(event.getTopic());
        message.setCreateTime(new Date());

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("userId", event.getUserId());
        content.put("entityType", event.getEntityType());
        content.put("entityId", event.getEntityId());
        if (event.getData() != null && !event.getData().isEmpty()) {
            content.putAll(event.getData());
        }
        try {
            message.setContent(objectMapper.writeValueAsString(content));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification content", e);
            return;
        }
        messageService.addMessage(message);
    }

    @KafkaListener(topics = {TOPIC_PUBLISH})
    public void handlePublishMessage(ConsumerRecord<String, String> record) {
        Event event = parseEvent(record);
        if (event == null) return;
        DiscussPost post = discussPostService.findDiscussPostById(event.getEntityId());
        if (post != null) {
            elasticSearchService.saveDiscussPost(post);
        }
    }

    @KafkaListener(topics = {TOPIC_DELETE})
    public void handleDeleteMessage(ConsumerRecord<String, String> record) {
        Event event = parseEvent(record);
        if (event == null) return;
        elasticSearchService.deleteDiscussPost(event.getEntityId());
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
