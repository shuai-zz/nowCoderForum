package com.example.search.infrastructure.mapper;

import com.example.post.domain.entity.DiscussPost;
import com.example.shared.result.PageData;

/**
 * Spring Data 仓储片段：放复杂自定义查询，避免在 application 层接触 ES 原生 API。
 *
 * @author zhaoshuai
 */
public interface DiscussPostRepositoryCustom {
    PageData<DiscussPost> searchByKeyword(String keyword, int pageNum, int pageSize);
}