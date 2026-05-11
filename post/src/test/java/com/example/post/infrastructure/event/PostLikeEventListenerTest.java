package com.example.post.infrastructure.event;

import com.example.post.infrastructure.mapper.DiscussPostMapper;
import com.example.shared.event.EntityLikedEvent;
import com.example.shared.event.EntityUnlikedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PostLikeEventListenerTest {

    @Mock
    private DiscussPostMapper discussPostMapper;

    @InjectMocks
    private PostLikeEventListener listener;

    @Test
    void onLikedIncrementsByOneForPostEntity() {
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_POST, 99, 7));
        verify(discussPostMapper).incrementLikeCount(99, 1);
    }

    @Test
    void onLikedIgnoresCommentEntity() {
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_COMMENT, 99, 7));
        verifyNoInteractions(discussPostMapper);
    }

    @Test
    void onLikedIgnoresUserEntity() {
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_USER, 99, 7));
        verifyNoInteractions(discussPostMapper);
    }

    @Test
    void onUnlikedDecrementsByOneForPostEntity() {
        listener.onUnliked(new EntityUnlikedEvent(1, ENTITY_TYPE_POST, 99, 7));
        verify(discussPostMapper).incrementLikeCount(99, -1);
    }

    @Test
    void onUnlikedIgnoresNonPostEntity() {
        listener.onUnliked(new EntityUnlikedEvent(1, ENTITY_TYPE_COMMENT, 99, 7));
        verify(discussPostMapper, never()).incrementLikeCount(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
