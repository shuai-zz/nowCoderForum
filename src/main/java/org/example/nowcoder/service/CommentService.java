package org.example.nowcoder.service;

import com.github.pagehelper.PageInfo;
import org.example.nowcoder.entity.Comment;

/**
 * @author zhaoshuai
 */
public interface CommentService {
    PageInfo<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize);
    int findCommentCount(int entityType, int entityId);

    int addComment(Comment comment);

    Comment findCommentById(int id);
}
