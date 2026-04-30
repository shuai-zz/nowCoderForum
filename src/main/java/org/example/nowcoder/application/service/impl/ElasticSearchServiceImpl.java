package org.example.nowcoder.application.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.domain.entity.DiscussPost;
import org.example.nowcoder.infrastructure.mapper.DiscussPostRepository;
import org.example.nowcoder.application.service.ElasticSearchService;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class ElasticSearchServiceImpl implements ElasticSearchService {
    private final DiscussPostRepository discussPostRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchTemplate elasticsearchTemplate;
    @Override
    public void saveDiscussPost(DiscussPost discussPost) {
        discussPostRepository.save(discussPost);
    }

    @Override
    public void deleteDiscussPost(int id) {
        discussPostRepository.deleteById(id);
    }

//    @Override
//    public Map<String, Object> searchDiscussPost(String keyWord, int pageNum, int pageSize) throws Exception {
//        HashMap<String, Object> result = new HashMap<>();
//
//        int safePageNum = Math.max(pageNum, 1);
//        int safePageSize = Math.max(pageSize, 1);
//
//        HighlightParameters highlightParameters = HighlightParameters.builder()
//                .withPreTags("<em>")
//                .withPostTags("</em>")
//                .withRequireFieldMatch(false)
//                .build();
//        Highlight highlight = new Highlight(
//                highlightParameters,
//                List.of(new HighlightField("title"), new HighlightField("content"))
//        );
//        HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);
//
//        NativeQuery query = NativeQuery.builder()
//                .withQuery(QueryBuilders.multiMatch()
//                        .query(keyWord)
//                        .fields("title", "content")
//                        .build()
//                        ._toQuery())
//                .withSort(Sort.by(
//                        Sort.Order.desc("type"),
//                        Sort.Order.desc("score"),
//                        Sort.Order.desc("createTime")
//                ))
//                .withPageable(PageRequest.of(safePageNum - 1, safePageSize))
//                .withHighlightQuery(highlightQuery)
//                .build();
//
//        SearchHits<DiscussPost> searchHits = elasticsearchOperations.search(query, DiscussPost.class);
//        result.put("count", (int) searchHits.getTotalHits());
//
//        List<DiscussPost> list = new ArrayList<>();
//        for (SearchHit<DiscussPost> hit : searchHits.getSearchHits()) {
//            DiscussPost discussPost = hit.getContent();
//            List<String> titleHighlights = hit.getHighlightField("title");
//            if (!titleHighlights.isEmpty()) {
//                discussPost.setTitle(titleHighlights.getFirst());
//            }
//            List<String> contentHighlights = hit.getHighlightField("content");
//            if (!contentHighlights.isEmpty()) {
//                discussPost.setContent(contentHighlights.getFirst());
//            }
//            list.add(discussPost);
//        }
//
//        result.put("data", list);
//        return result;
//    }
    public Page<DiscussPost> searchDiscussPost(String keyWord, int pageNum, int pageSize){
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
        HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

        // 创建查询条件
        NativeQuery query = NativeQuery.builder()
                .withQuery(QueryBuilders.multiMatch()
                        .query(keyWord)
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

        // 执行查询并获取结果
        SearchHits<DiscussPost> searchHits = elasticsearchOperations.search(query, DiscussPost.class);

        // 提取高亮内容并创建返回列表
        List<DiscussPost> list=new ArrayList<>();
        for(var hit:searchHits.getSearchHits()){
            DiscussPost post = hit.getContent();

            // 处理标题高亮
            List<String> titleHighlights = hit.getHighlightField("title");
            if(!titleHighlights.isEmpty()){
                post.setTitle(titleHighlights.getFirst());
            }

            // 处理内容高亮
            List<String> contentHighlights = hit.getHighlightField("content");
            if(!contentHighlights.isEmpty()){
                post.setContent(contentHighlights.getFirst());
            }

            list.add(post);
        }
        return new PageImpl<>(list, PageRequest.of(pageNum - 1, pageSize), searchHits.getTotalHits());
    }
}
