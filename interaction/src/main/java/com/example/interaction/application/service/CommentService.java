package com.example.interaction.application.service;

import com.example.interaction.application.dto.CommentWithLike;
import com.example.interaction.domain.entity.Comment;
import com.example.shared.result.PageData;

/**
 * @author zhaoshuai
 */
public interface CommentService{

    PageData<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize);

    int findCommentCount(int entityType, int entityId);

    int addComment(Comment comment);

    Comment findCommentById(int id);

    PageData<CommentWithLike> findCommentsWithLike(int entityType, int entityId, int pageNum, int pageSize, int currentUserId);

}