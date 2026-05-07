package com.example.search.infrastructure.mapper;

import com.example.search.domain.SearchablePost;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * @author zhaoshuai
 */
@Repository
public interface SearchablePostRepository
        extends ElasticsearchRepository<SearchablePost, Integer>,
        SearchablePostRepositoryCustom {
}
