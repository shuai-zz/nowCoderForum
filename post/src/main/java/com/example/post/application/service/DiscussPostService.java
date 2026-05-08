package com.example.post.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.post.application.dto.PostItem;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.result.PageData;


/**
 * @author 23211
 */
public interface DiscussPostService{
    PageData<PostItem> selectDiscussPosts(int pageNum, int pageSize, int userId);
    int insertDiscussPost(DiscussPost discussPost);

    PostItem findDiscussPostById(int discussPostId, int userId);

    void markAsTop(DiscussPost post);
    void markAsWonderful(DiscussPost post);
    void softDelete(DiscussPost post);
    void refreshCommentCount(int postId, int count);
    void updateScore(int postId, double score);

    /**
     * 根据 ID 查询帖子（裸查，不连带作者信息）。
     */
    DiscussPost getRawPost(int postId);
    /**
     * 标记帖子需要重新计算分数（加入 Quartz 刷新队列）。
     */
    void markForScoreRefresh(int postId);
}
