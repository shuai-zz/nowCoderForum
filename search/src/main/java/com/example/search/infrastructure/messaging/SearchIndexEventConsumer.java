package com.example.search.infrastructure.messaging;

import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.search.application.service.ElasticSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.nowcoder.domain.entity.Event;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.example.shared.constant.ForumConstant.TOPIC_DELETE;
import static com.example.shared.constant.ForumConstant.TOPIC_PUBLISH;


/**
 * 搜索索引事件消费者：处理帖子发布/删除事件，同步 Elasticsearch 索引。
 * 归属 search 模块（待实体迁移后迁入）。
 * @author zhaoshuai
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchIndexEventConsumer {

    private final DiscussPostService discussPostService;
    private final ElasticSearchService elasticSearchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {TOPIC_PUBLISH})
    public void handlePublish(ConsumerRecord<String, String> record) {
        Event event = parseEvent(record);
        if (event == null) {
            return;
        }
        DiscussPost post = discussPostService.findDiscussPostById(event.getEntityId(), 0).discussPost();
        if (post != null) {
            elasticSearchService.saveDiscussPost(post);
        }
    }

    @KafkaListener(topics = {TOPIC_DELETE})
    public void handleDelete(ConsumerRecord<String, String> record) {
        Event event = parseEvent(record);
        if (event == null) {
            return;
        }
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