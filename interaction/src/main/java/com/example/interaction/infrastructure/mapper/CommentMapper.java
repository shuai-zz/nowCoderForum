package com.example.interaction.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interaction.domain.entity.Comment;
import com.example.shared.common.result.PageData;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author zhaoshuai
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    default List<Comment> selectCommentsByEntity(Page<Comment> page, int entityType, int entityId){
        return selectList(page, Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getEntityType, entityType)
                .eq(Comment::getEntityId, entityId)
                .orderByDesc(Comment::getCreateTime));
    }
}