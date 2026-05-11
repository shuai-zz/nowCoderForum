package com.example.interaction.domain.entity;

import com.example.shared.constant.ForumConstant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    @Test
    void isOnPostReturnsTrueWhenEntityTypeIsPost() {
        Comment c = Comment.builder().entityType(ForumConstant.ENTITY_TYPE_POST).build();
        assertThat(c.isOnPost()).isTrue();
        assertThat(c.isReply()).isFalse();
    }

    @Test
    void isReplyReturnsTrueWhenEntityTypeIsComment() {
        Comment c = Comment.builder().entityType(ForumConstant.ENTITY_TYPE_COMMENT).build();
        assertThat(c.isReply()).isTrue();
        assertThat(c.isOnPost()).isFalse();
    }

    @Test
    void neitherOnPostNorReplyForUnknownEntityType() {
        Comment c = Comment.builder().entityType(ForumConstant.ENTITY_TYPE_USER).build();
        assertThat(c.isOnPost()).isFalse();
        assertThat(c.isReply()).isFalse();
    }

    @Test
    void applySanitizedContentOnlyTouchesContent() {
        Comment c = Comment.builder()
                .id(1)
                .userId(2)
                .entityType(ForumConstant.ENTITY_TYPE_POST)
                .entityId(3)
                .targetId(4)
                .content("raw <script>")
                .likeCount(7)
                .replyCount(2)
                .status(0)
                .build();

        c.applySanitizedContent("safe");

        assertThat(c.getContent()).isEqualTo("safe");
        assertThat(c.getId()).isEqualTo(1);
        assertThat(c.getUserId()).isEqualTo(2);
        assertThat(c.getEntityId()).isEqualTo(3);
        assertThat(c.getTargetId()).isEqualTo(4);
        assertThat(c.getLikeCount()).isEqualTo(7);
        assertThat(c.getReplyCount()).isEqualTo(2);
    }

    @Test
    void noArgsConstructorYieldsDefaults() {
        Comment c = new Comment();
        assertThat(c.getId()).isZero();
        assertThat(c.getLikeCount()).isZero();
        assertThat(c.getReplyCount()).isZero();
        assertThat(c.getContent()).isNull();
    }
}
