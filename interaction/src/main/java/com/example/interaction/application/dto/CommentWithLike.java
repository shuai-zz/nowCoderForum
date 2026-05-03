package com.example.interaction.application.dto;

import com.example.interaction.domain.entity.Comment;

public record CommentWithLike(Comment comment, long likeCount, int likeStatus) {
    public static CommentWithLike of(Comment comment, long likeCount, int likeStatus) {
        return new CommentWithLike(comment, likeCount, likeStatus);
    }
}
