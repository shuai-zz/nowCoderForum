package com.example.interaction.infrastructure.event;

import com.example.interaction.infrastructure.mapper.CommentMapper;
import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;

/**
 * 评论点赞数读模型维护：监听领域事件，增减 comment.like_count。
 * 设计与 {@code post.PostLikeEventListener} 对称：
 * 评论的点赞数和帖子一样是固有展示属性，持久化到主表，不依赖 Redis 留存。
 *
 * @author zhaoshuai
 */
@Component
@RequiredArgsConstructor
public class CommentLikeEventListener {

    private final CommentMapper commentMapper;

    @EventListener
    public void onLiked(EntityLikedEvent event) {
        if (event.entityType() == ENTITY_TYPE_COMMENT) {
            commentMapper.incrementLikeCount(event.entityId(), 1);
        }
    }

    @EventListener
    public void onUnliked(EntityUnlikedEvent event) {
        if (event.entityType() == ENTITY_TYPE_COMMENT) {
            commentMapper.incrementLikeCount(event.entityId(), -1);
        }
    }
}