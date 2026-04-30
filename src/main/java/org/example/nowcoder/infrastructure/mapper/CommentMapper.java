package org.example.nowcoder.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.nowcoder.domain.entity.Comment;

import java.util.List;

/**
 * @author zhaoshuai
 */
@Mapper
public interface CommentMapper {
    List<Comment> selectCommentsByEntity(int entityType, int entityId);
    int selectCountByEntity(int entityType, int entityId);
    int insertComment(Comment comment);
    Comment selectCommentById(int id);
}
