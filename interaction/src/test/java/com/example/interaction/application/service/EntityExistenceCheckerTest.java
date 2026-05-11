package com.example.interaction.application.service;

import com.example.interaction.domain.entity.Comment;
import com.example.interaction.infrastructure.mapper.CommentMapper;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.exception.ResourceNotFoundException;
import com.example.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityExistenceCheckerTest {

    @Mock
    private DiscussPostService discussPostService;

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private EntityExistenceChecker checker;

    @Test
    void postExistsAndNotDeletedPasses() {
        DiscussPost post = DiscussPost.builder()
                .id(99).userId(1).status(DiscussPost.STATUS_NORMAL).build();
        when(discussPostService.getRawPost(99)).thenReturn(post);

        assertThatCode(() -> checker.requireExists(ENTITY_TYPE_POST, 99))
                .doesNotThrowAnyException();
    }

    @Test
    void missingPostThrowsResourceNotFound() {
        when(discussPostService.getRawPost(99)).thenReturn(null);

        assertThatThrownBy(() -> checker.requireExists(ENTITY_TYPE_POST, 99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found: 99");
    }

    @Test
    void softDeletedPostThrowsResourceNotFound() {
        // 即使行存在，软删的帖子也不应让点赞/评论写入 —— 否则产生孤儿计数
        DiscussPost deleted = DiscussPost.builder()
                .id(99).userId(1).status(DiscussPost.STATUS_DELETED).build();
        when(discussPostService.getRawPost(99)).thenReturn(deleted);

        assertThatThrownBy(() -> checker.requireExists(ENTITY_TYPE_POST, 99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void commentExistsAndStatusZeroPasses() {
        Comment c = Comment.builder().id(50).status(0).build();
        when(commentMapper.selectById(50)).thenReturn(c);

        assertThatCode(() -> checker.requireExists(ENTITY_TYPE_COMMENT, 50))
                .doesNotThrowAnyException();
    }

    @Test
    void missingCommentThrowsResourceNotFound() {
        when(commentMapper.selectById(50)).thenReturn(null);

        assertThatThrownBy(() -> checker.requireExists(ENTITY_TYPE_COMMENT, 50))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Comment not found: 50");
    }

    @Test
    void nonZeroCommentStatusThrowsResourceNotFound() {
        // status != 0 视为不可用（被删除 / 屏蔽等）
        Comment hidden = Comment.builder().id(50).status(2).build();
        when(commentMapper.selectById(50)).thenReturn(hidden);

        assertThatThrownBy(() -> checker.requireExists(ENTITY_TYPE_COMMENT, 50))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unsupportedEntityTypeThrowsValidation() {
        // ENTITY_TYPE_USER = 3 等不在 switch 中
        assertThatThrownBy(() -> checker.requireExists(999, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported entity type");
    }
}
