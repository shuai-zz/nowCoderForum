package com.example.search.interfaces.rest;

import com.example.post.application.dto.PostItem;
import com.example.post.interfaces.vo.PostListItemVO;
import com.example.search.application.service.ElasticSearchService;
import com.example.shared.result.PageData;
import com.example.shared.result.PageResult;
import com.example.shared.result.Result;
import com.example.user.application.service.UserService;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;



/**
 * @author zhaoshuai
 */
@Tag(name = "Search", description = "帖子全文搜索（Elasticsearch）")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final ElasticSearchService elasticSearchService;

    @Operation(summary = "关键词搜索帖子")
    @GetMapping
    public Result<PageResult<PostListItemVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) throws Exception {
        if (StringUtils.isBlank(keyword)) {
            return Result.ok(PageResult.empty(pageNum, pageSize));
        }

        PageData<PostItem> pageData = elasticSearchService.searchDiscussPost(keyword, pageNum, pageSize);
        List<PostListItemVO> list = pageData.items().stream()
                .map(item ->
                    PostListItemVO.of(item.discussPost(), UserVO.from(item.author()), item.likeCount())
                ).toList();
        return Result.ok(PageResult.of(list, pageData.total(), pageNum, pageSize));
    }
}
