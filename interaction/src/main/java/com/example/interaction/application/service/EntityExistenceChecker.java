package com.example.interaction.application.service;

import com.example.interaction.domain.entity.Comment;
import com.example.interaction.infrastructure.mapper.CommentMapper;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.exception.ResourceNotFoundException;
import com.example.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;

/**
 * 写操作（点赞 / 评论）入口的 entity 存在性守卫。
 * 防止用户对不存在/已软删的实体写入，避免脏 Redis 计数与孤儿评论。
 *
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
public class EntityExistenceChecker {

    private final DiscussPostService discussPostService;
    private final CommentMapper commentMapper;

    public void requireExists(int entityType, int entityId) {
        switch (entityType) {
            case ENTITY_TYPE_POST -> {
                DiscussPost post = discussPostService.getRawPost(entityId);
                if (post == null || post.isDeleted()) {
                    throw new ResourceNotFoundException("Post not found: " + entityId);
                }
            }
            case ENTITY_TYPE_COMMENT -> {
                Comment comment = commentMapper.selectById(entityId);
                if (comment == null || comment.getStatus() != 0) {
                    throw new ResourceNotFoundException("Comment not found: " + entityId);
                }
            }
            default -> throw new ValidationException("Unsupported entity type: " + entityType);
        }
    }
}
