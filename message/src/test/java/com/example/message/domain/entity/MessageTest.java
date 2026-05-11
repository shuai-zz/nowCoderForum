package com.example.message.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void builderPopulatesAllFields() {
        Date now = new Date();
        Message m = Message.builder()
                .id(1)
                .fromId(10)
                .toId(20)
                .conversationId("10_20")
                .content("hi")
                .status(0)
                .createTime(now)
                .build();

        assertThat(m.getId()).isEqualTo(1);
        assertThat(m.getFromId()).isEqualTo(10);
        assertThat(m.getToId()).isEqualTo(20);
        assertThat(m.getConversationId()).isEqualTo("10_20");
        assertThat(m.getContent()).isEqualTo("hi");
        assertThat(m.getStatus()).isZero();
        assertThat(m.getCreateTime()).isSameAs(now);
    }

    @Test
    void applySanitizedContentOverwritesOnlyContent() {
        Message m = Message.builder()
                .id(7)
                .fromId(1)
                .toId(2)
                .conversationId("1_2")
                .content("raw")
                .status(1)
                .build();

        m.applySanitizedContent("safe");

        assertThat(m.getContent()).isEqualTo("safe");
        assertThat(m.getId()).isEqualTo(7);
        assertThat(m.getFromId()).isEqualTo(1);
        assertThat(m.getToId()).isEqualTo(2);
        assertThat(m.getConversationId()).isEqualTo("1_2");
        assertThat(m.getStatus()).isEqualTo(1);
    }

    @Test
    void applySanitizedContentAcceptsNull() {
        Message m = Message.builder().content("raw").build();
        m.applySanitizedContent(null);
        assertThat(m.getContent()).isNull();
    }
}
