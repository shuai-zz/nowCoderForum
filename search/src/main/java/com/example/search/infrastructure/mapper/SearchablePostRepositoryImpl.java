package com.example.search.infrastructure.mapper;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.example.search.domain.SearchablePost;
import com.example.shared.result.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data 命名约定：实现类必须叫 {主接口}Impl 且与主接口同包，
 * 才会被自动织入到 SearchablePostRepository。
 *
 * @author zhaoshuai
 */
@RequiredArgsConstructor
public class SearchablePostRepositoryImpl implements SearchablePostRepositoryCustom {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public PageData<SearchablePost> searchByKeyword(String keyword, int pageNum, int pageSize) {
        // 创建高亮查询
        HighlightParameters highlightParameters = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withRequireFieldMatch(false)
                .build();
        Highlight highlight = new Highlight(
                highlightParameters,
                List.of(new HighlightField("title"), new HighlightField("content"))
        );
        HighlightQuery highlightQuery = new HighlightQuery(highlight, SearchablePost.class);

        // 创建查询
        NativeQuery query = NativeQuery.builder()
                .withQuery(QueryBuilders.multiMatch()
                        .query(keyword)
                        .fields("title", "content")
                        .build()
                        ._toQuery())
                .withSort(Sort.by(
                        Sort.Order.desc("type"),
                        Sort.Order.desc("score"),
                        Sort.Order.desc("createTime")
                ))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .withHighlightQuery(highlightQuery)
                .build();

        SearchHits<SearchablePost> searchHits = elasticsearchOperations.search(query, SearchablePost.class);

        List<SearchablePost> list = new ArrayList<>(searchHits.getSearchHits().size());
        for (var hit : searchHits.getSearchHits()) {
            SearchablePost post = hit.getContent();
            List<String> titleHighlights = hit.getHighlightField("title");
            if (!titleHighlights.isEmpty()) {
                post.setTitle(titleHighlights.getFirst());
            }
            List<String> contentHighlights = hit.getHighlightField("content");
            if (!contentHighlights.isEmpty()) {
                post.setContent(contentHighlights.getFirst());
            }
            list.add(post);
        }
        return new PageData<>(list, searchHits.getTotalHits());
    }
}