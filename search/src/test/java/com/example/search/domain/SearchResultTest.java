package com.example.search.domain;

import com.example.shared.dto.AuthorRef;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultTest {

    @Test
    void ofPopulatesAllAccessors() {
        Date now = new Date();
        AuthorRef author = AuthorRef.of(42, "alice", "/a.png");

        SearchResult r = SearchResult.of(
                1, 42, author,
                "raw title", "raw content",
                "<em>raw</em> title", "<em>raw</em> content",
                0, 0,
                5, 6L, 1.23,
                now
        );

        assertThat(r.id()).isEqualTo(1);
        assertThat(r.userId()).isEqualTo(42);
        assertThat(r.author()).isSameAs(author);
        assertThat(r.title()).isEqualTo("raw title");
        assertThat(r.content()).isEqualTo("raw content");
        assertThat(r.highlightTitle()).isEqualTo("<em>raw</em> title");
        assertThat(r.highlightContent()).isEqualTo("<em>raw</em> content");
        assertThat(r.type()).isZero();
        assertThat(r.status()).isZero();
        assertThat(r.commentCount()).isEqualTo(5);
        assertThat(r.likeCount()).isEqualTo(6L);
        assertThat(r.score()).isEqualTo(1.23);
        assertThat(r.createTime()).isSameAs(now);
    }

    @Test
    void recordEqualsHashCodeOnAllComponents() {
        Date now = new Date();
        AuthorRef author = AuthorRef.of(1, "a", null);
        SearchResult a = SearchResult.of(1, 1, author, "t", "c", "ht", "hc", 0, 0, 0, 0L, 0.0, now);
        SearchResult b = SearchResult.of(1, 1, author, "t", "c", "ht", "hc", 0, 0, 0, 0L, 0.0, now);
        SearchResult diff = SearchResult.of(2, 1, author, "t", "c", "ht", "hc", 0, 0, 0, 0L, 0.0, now);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(diff);
    }

    @Test
    void authorFieldMayBeNullUntilFilledByService() {
        // Repository 返回时 author == null，由 Service 填充
        SearchResult r = SearchResult.of(1, 1, null, "t", "c", null, null, 0, 0, 0, 0L, 0.0, new Date());
        assertThat(r.author()).isNull();
    }
}
