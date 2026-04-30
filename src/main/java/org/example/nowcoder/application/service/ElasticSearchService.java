package org.example.nowcoder.application.service;

import org.example.nowcoder.domain.entity.DiscussPost;
import org.springframework.data.domain.Page;

/**
 * @author zhaoshuai
 */
public interface ElasticSearchService {
    void saveDiscussPost(DiscussPost discussPost);
    void deleteDiscussPost(int id);
    Page<DiscussPost> searchDiscussPost(String keyWord, int pageNum, int pageSize) throws Exception;
}
