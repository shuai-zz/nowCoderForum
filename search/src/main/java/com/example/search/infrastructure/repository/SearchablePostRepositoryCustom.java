package com.example.search.infrastructure.repository;

import com.example.search.domain.SearchResult;
import com.example.shared.result.PageData;

/**
 * Spring Data 仓储片段：放复杂自定义查询，避免在 application 层接触 ES 原生 API。
 *
 * @author zhaoshuai
 */
public interface SearchablePostRepositoryCustom {
    PageData<SearchResult> searchByKeyword(String keyword, int pageNum, int pageSize);
}