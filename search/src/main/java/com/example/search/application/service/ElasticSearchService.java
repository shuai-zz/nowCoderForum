package com.example.search.application.service;

import com.example.post.application.dto.PostItem;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.result.PageData;

/**
 * @author zhaoshuai
 */
public interface ElasticSearchService {
    void saveDiscussPost(DiscussPost discussPost);
    void deleteDiscussPost(int id);
    PageData<PostItem> searchDiscussPost(String keyWord, int pageNum, int pageSize) throws Exception;
}
