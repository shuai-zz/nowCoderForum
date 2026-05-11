package com.example.shared.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorRefTest {

    @Test
    void ofPopulatesComponents() {
        AuthorRef r = AuthorRef.of(7, "alice", "/avatar/alice.png");
        assertThat(r.id()).isEqualTo(7);
        assertThat(r.username()).isEqualTo("alice");
        assertThat(r.avatarUrl()).isEqualTo("/avatar/alice.png");
    }

    @Test
    void deletedPlaceholderHasZeroIdAndDeletedTag() {
        AuthorRef r = AuthorRef.deleted();
        assertThat(r.id()).isZero();
        assertThat(r.username()).isEqualTo("[deleted]");
        assertThat(r.avatarUrl()).isNull();
    }

    @Test
    void deletedReturnsNewInstanceButEqualByValue() {
        // record 的语义相等 —— 不同实例值相等
        assertThat(AuthorRef.deleted()).isEqualTo(AuthorRef.deleted());
    }

    @Test
    void recordEqualsByComponents() {
        assertThat(AuthorRef.of(1, "a", "u")).isEqualTo(AuthorRef.of(1, "a", "u"));
        assertThat(AuthorRef.of(1, "a", "u")).isNotEqualTo(AuthorRef.of(2, "a", "u"));
    }
}
