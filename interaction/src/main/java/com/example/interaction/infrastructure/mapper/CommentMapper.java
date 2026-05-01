package com.example.interaction.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interaction.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author zhaoshuai
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}