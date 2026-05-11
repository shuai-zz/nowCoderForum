package com.example.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventProducer producer;

    @Test
    void fireEventSerializesAndSendsToConfiguredTopic() throws Exception {
        Event event = new Event().setTopic("like").setUserId(1).setEntityId(99);
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"topic\":\"like\"}");

        producer.fireEvent(event);

        verify(kafkaTemplate).send("like", "{\"topic\":\"like\"}");
    }

    @Test
    void fireEventAutoFillsEventIdWhenAbsent() throws Exception {
        Event event = new Event().setTopic("publish");
        when(objectMapper.writeValueAsString(any(Event.class))).thenReturn("{}");

        producer.fireEvent(event);

        // UUID 形如 xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertThat(event.getEventId()).isNotNull().matches("[0-9a-f-]{36}");
    }

    @Test
    void fireEventPreservesExternallySetEventId() throws Exception {
        Event event = new Event().setTopic("publish").setEventId("preset-id");
        when(objectMapper.writeValueAsString(any(Event.class))).thenReturn("{}");

        producer.fireEvent(event);

        assertThat(event.getEventId()).isEqualTo("preset-id");
    }

    @Test
    void serializationFailureThrowsIllegalStateAndDoesNotSend() throws Exception {
        // S2 修复：序列化失败必须抛出，而不是 swallow + log；否则下游通知/索引静默丢失
        Event event = new Event().setTopic("publish");
        when(objectMapper.writeValueAsString(any(Event.class)))
                .thenThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> producer.fireEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Event serialization failed")
                .hasMessageContaining("publish")
                .hasRootCauseInstanceOf(JsonProcessingException.class);

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void fireEventCarriesTopicFromEventInstance() throws Exception {
        Event event = new Event().setTopic("follow");
        when(objectMapper.writeValueAsString(any(Event.class))).thenReturn("{}");

        producer.fireEvent(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("follow");
    }
}
