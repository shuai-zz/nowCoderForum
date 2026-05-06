package com.example.post.infrastructure.event;

import com.example.post.infrastructure.mapper.DiscussPostMapper;
import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;

@Component
@RequiredArgsConstructor
public class PostLikeEventListener {

    private final DiscussPostMapper discussPostMapper;

    @EventListener
    public void onLiked(EntityLikedEvent event) {
        if (event.entityType() == ENTITY_TYPE_POST) {
            discussPostMapper.incrementLikeCount(event.entityId(), 1);
        }
    }

    @EventListener
    public void onUnliked(EntityUnlikedEvent event) {
        if (event.entityType() == ENTITY_TYPE_POST) {
            discussPostMapper.incrementLikeCount(event.entityId(), -1);
        }
    }
}
