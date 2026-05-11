package com.example.interaction.infrastructure.event;

import com.example.interaction.infrastructure.mapper.CommentMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CommentLikeEventListenerTest {

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentLikeEventListener listener;

    @Test
    void onLikedIncrementsByOneForCommentEntity() {
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_COMMENT, 88, 7));
        verify(commentMapper).incrementLikeCount(88, 1);
    }

    @Test
    void onLikedIgnoresPostEntity() {
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_POST, 88, 7));
        verifyNoInteractions(commentMapper);
    }

    @Test
    void onLikedIgnoresUserEntity() {
        listener.onLiked(new EntityLikedEvent(1, ENTITY_TYPE_USER, 88, 7));
        verifyNoInteractions(commentMapper);
    }

    @Test
    void onUnlikedDecrementsByOneForCommentEntity() {
        listener.onUnliked(new EntityUnlikedEvent(1, ENTITY_TYPE_COMMENT, 88, 7));
        verify(commentMapper).incrementLikeCount(88, -1);
    }

    @Test
    void onUnlikedIgnoresPostEntity() {
        listener.onUnliked(new EntityUnlikedEvent(1, ENTITY_TYPE_POST, 88, 7));
        verifyNoInteractions(commentMapper);
    }
}
