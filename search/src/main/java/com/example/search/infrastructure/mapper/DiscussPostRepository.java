package com.example.search.infrastructure.mapper;

import com.example.post.domain.entity.DiscussPost;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * @author zhaoshuai
 */
@Repository
public interface DiscussPostRepository
        extends ElasticsearchRepository<DiscussPost, Integer>,
                DiscussPostRepositoryCustom {
}
