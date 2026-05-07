package com.example.search.infrastructure.mapper;

import com.example.search.domain.SearchablePost;
import com.example.shared.result.PageData;

/**
 * Spring Data 仓储片段：放复杂自定义查询，避免在 application 层接触 ES 原生 API。
 *
 * @author zhaoshuai
 */
public interface SearchablePostRepositoryCustom {
    PageData<SearchablePost> searchByKeyword(String keyword, int pageNum, int pageSize);
}