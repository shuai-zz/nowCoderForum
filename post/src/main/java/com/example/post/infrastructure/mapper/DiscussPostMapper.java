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

    @SuppressWarnings({"MybatisPlusMapperMethodInspection"})
    default int incrementLikeCount(int id, int delta) {
        return update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .setSql("like_count = like_count + {0}", delta)
                .eq(DiscussPost::getId, id));
    }

    @SuppressWarnings({"MybatisPlusMapperMethodInspection"})
    default int updateCommentCount(int id, int count) {
        return update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .set(DiscussPost::getCommentCount, count)
                .eq(DiscussPost::getId, id));
    }

    @SuppressWarnings({"MybatisPlusMapperMethodInspection"})
    default int updateScore(int id, double score) {
        return update(null, Wrappers.<DiscussPost>lambdaUpdate()
                .set(DiscussPost::getScore, score)
                .eq(DiscussPost::getId, id));
    }
}