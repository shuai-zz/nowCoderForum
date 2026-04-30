package org.example.nowcoder.infrastructure.mapper;

import org.example.nowcoder.domain.entity.DiscussPost;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;

/**
 * @author zhaoshuai
 */
@Repository
public interface DiscussPostRepository extends ElasticsearchRepository<DiscussPost, Integer> {
}
