package com.example.search.domain;

import com.example.post.domain.entity.DiscussPost;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class SearchablePostTest {

    @Test
    void fromCopiesAllFieldsOutOfDiscussPost() {
        Date now = new Date();
        DiscussPost p = DiscussPost.builder()
                .id(101)
                .userId(7)
                .title("title")
                .content("content")
                .type(DiscussPost.TYPE_TOP)
                .status(DiscussPost.STATUS_WONDERFUL)
                .createTime(now)
                .commentCount(12)
                .likeCount(34)
                .score(56.78)
                .build();

        SearchablePost s = SearchablePost.from(p);

        assertThat(s.getId()).isEqualTo(101);
        assertThat(s.getUserId()).isEqualTo(7);
        assertThat(s.getTitle()).isEqualTo("title");
        assertThat(s.getContent()).isEqualTo("content");
        assertThat(s.getType()).isEqualTo(DiscussPost.TYPE_TOP);
        assertThat(s.getStatus()).isEqualTo(DiscussPost.STATUS_WONDERFUL);
        assertThat(s.getCreateTime()).isSameAs(now);
        assertThat(s.getCommentCount()).isEqualTo(12);
        assertThat(s.getLikeCount()).isEqualTo(34);
        assertThat(s.getScore()).isEqualTo(56.78);
    }

    @Test
    void toDiscussPostRoundTripsAllFields() {
        Date now = new Date();
        SearchablePost s = new SearchablePost();
        s.setId(9);
        s.setUserId(5);
        s.setTitle("<em>highlighted</em> title");
        s.setContent("<em>highlighted</em> content");
        s.setType(DiscussPost.TYPE_NORMAL);
        s.setStatus(DiscussPost.STATUS_NORMAL);
        s.setCreateTime(now);
        s.setCommentCount(2);
        s.setLikeCount(3);
        s.setScore(1.23);

        DiscussPost p = s.toDiscussPost();

        assertThat(p.getId()).isEqualTo(9);
        assertThat(p.getUserId()).isEqualTo(5);
        // highlight 标签可保留在 title/content 内 —— ES → domain 是值拷贝，不剥离标签
        assertThat(p.getTitle()).isEqualTo("<em>highlighted</em> title");
        assertThat(p.getContent()).isEqualTo("<em>highlighted</em> content");
        assertThat(p.getType()).isEqualTo(DiscussPost.TYPE_NORMAL);
        assertThat(p.getStatus()).isEqualTo(DiscussPost.STATUS_NORMAL);
        assertThat(p.getCreateTime()).isSameAs(now);
        assertThat(p.getCommentCount()).isEqualTo(2);
        assertThat(p.getLikeCount()).isEqualTo(3);
        assertThat(p.getScore()).isEqualTo(1.23);
    }

    @Test
    void roundTripFromDiscussPostIsLossless() {
        Date now = new Date();
        DiscussPost original = DiscussPost.builder()
                .id(1).userId(2).title("t").content("c")
                .type(0).status(0).createTime(now)
                .commentCount(0).likeCount(0).score(0.0)
                .build();

        DiscussPost roundTripped = SearchablePost.from(original).toDiscussPost();

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getTitle()).isEqualTo(original.getTitle());
        assertThat(roundTripped.getContent()).isEqualTo(original.getContent());
        assertThat(roundTripped.getType()).isEqualTo(original.getType());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getCreateTime()).isEqualTo(original.getCreateTime());
        assertThat(roundTripped.getCommentCount()).isEqualTo(original.getCommentCount());
        assertThat(roundTripped.getLikeCount()).isEqualTo(original.getLikeCount());
        assertThat(roundTripped.getScore()).isEqualTo(original.getScore());
    }
}
