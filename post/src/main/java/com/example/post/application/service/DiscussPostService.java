package com.example.post.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.post.application.dto.PostListItem;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.common.result.PageData;

import java.util.List;


/**
 * @author 23211
 */
public interface DiscussPostService extends IService<DiscussPost> {
    PageData<PostListItem> selectDiscussPosts(int pageNum, int pageSize, int userId);
    int insertDiscussPost(DiscussPost discussPost);

    DiscussPost findDiscussPostById(int discussPostId);

    int updateCommentCount(int entityId, int count);
    int updateType(int entityId, int type);
    int updateStatus(int entityId, int status);

    void updateScore(int postId, double score);
}
