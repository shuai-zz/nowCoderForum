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

    int updateCommentCount(int entityId, int count);
    int updateType(int entityId, int type);
    int updateStatus(int entityId, int status);

    void updateScore(int postId, double score);
}
