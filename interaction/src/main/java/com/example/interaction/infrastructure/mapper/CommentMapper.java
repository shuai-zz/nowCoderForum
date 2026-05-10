package com.example.interaction.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interaction.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author zhaoshuai
 */
@SuppressWarnings("MybatisPlusMapperMethodInspection")
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    default List<Comment> selectCommentsByEntity(Page<Comment> page, int entityType, int entityId){
        return selectList(page, Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getEntityType, entityType)
                .eq(Comment::getEntityId, entityId)
                .orderByDesc(Comment::getCreateTime));
    }

    default int incrementLikeCount(int id, int delta) {
        return update(null, Wrappers.<Comment>lambdaUpdate()
                .setSql("like_count = like_count + {0}", delta)
                .eq(Comment::getId, id));
    }

    default int refreshReplyCount(int parentId, int count){
        return update(null, Wrappers.<Comment>lambdaUpdate()
                .set(Comment::getReplyCount, count)
                .eq(Comment::getId, parentId));
    }

    List<Comment> selectTopRepliesGrouped(@Param("parentIds") List<Integer> parentIds,
                                          @Param("limit") int limit);
}