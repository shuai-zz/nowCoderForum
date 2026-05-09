package com.example.search.application.service;

import com.example.post.domain.entity.DiscussPost;
import com.example.search.domain.SearchResult;
import com.example.shared.result.PageData;

/**
 * @author zhaoshuai
 */
public interface ElasticSearchService {
    void saveDiscussPost(DiscussPost discussPost);
    void deleteDiscussPost(int id);
    PageData<SearchResult> searchDiscussPost(String keyWord, int pageNum, int pageSize);
}
