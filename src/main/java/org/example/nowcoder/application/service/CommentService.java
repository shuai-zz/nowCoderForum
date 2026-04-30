package org.example.nowcoder.application.service;

import com.github.pagehelper.PageInfo;
import org.example.nowcoder.domain.entity.Comment;

/**
 * @author zhaoshuai
 */
public interface CommentService {
    PageInfo<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize);
    int findCommentCount(int entityType, int entityId);

    int addComment(Comment comment);

    Comment findCommentById(int id);
}
