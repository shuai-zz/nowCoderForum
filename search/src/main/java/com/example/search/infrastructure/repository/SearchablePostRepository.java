package com.example.search.infrastructure.repository;

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
