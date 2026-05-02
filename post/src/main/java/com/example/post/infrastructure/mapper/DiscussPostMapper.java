package com.example.post.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.post.domain.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author 23211
 */
@Mapper
public interface DiscussPostMapper extends BaseMapper<DiscussPost> {
    default List<DiscussPost> selectDiscussPosts(Page<DiscussPost> page, int userId) {
        return selectList(page, Wrappers.<DiscussPost>lambdaQuery()
                .eq(userId != 0, DiscussPost::getUserId, userId)
                .orderByDesc(DiscussPost::getCreateTime));
    }

    default int updateCommentCount(int id, int commentCount) {
        return update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .set(DiscussPost::getCommentCount, commentCount)
                .eq(DiscussPost::getId, id));
    }

    default int updateType(int id, int type) {
        return update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .set(DiscussPost::getType, type)
                .eq(DiscussPost::getId, id));
    }

    default int updateStatus(int id, int status) {
        return update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .set(DiscussPost::getStatus, status)
                .eq(DiscussPost::getId, id));
    }

    default void updateScore(int postId, double score) {
        update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .set(DiscussPost::getScore, score)
                .eq(DiscussPost::getId, postId));
    }
}