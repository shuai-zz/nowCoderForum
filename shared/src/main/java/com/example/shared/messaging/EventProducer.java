package com.example.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.domain.entity.Event;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void fireEvent(Event event) {
        try {
            kafkaTemplate.send(event.getTopic(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
        }
    }
}
