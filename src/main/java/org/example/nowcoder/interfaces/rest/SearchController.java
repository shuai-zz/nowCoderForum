package org.example.nowcoder.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.example.nowcoder.domain.entity.DiscussPost;
import org.example.nowcoder.application.service.ElasticSearchService;
import org.example.nowcoder.application.service.LikeService;
import org.example.nowcoder.application.service.UserService;
import org.example.nowcoder.interfaces.common.PageResult;
import org.example.nowcoder.interfaces.common.Result;
import org.example.nowcoder.interfaces.vo.PostListItemVO;
import org.example.nowcoder.interfaces.vo.UserVO;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.example.nowcoder.infrastructure.util.ForumConstant.ENTITY_TYPE_POST;

@Tag(name = "Search", description = "帖子全文搜索（Elasticsearch）")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final ElasticSearchService elasticSearchService;
    private final UserService userService;
    private final LikeService likeService;

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

        Page<DiscussPost> result = elasticSearchService.searchDiscussPost(keyword, pageNum, pageSize);
        List<PostListItemVO> items = new ArrayList<>();
        if (result != null) {
            for (DiscussPost post : result) {
                UserVO author = UserVO.from(userService.getById(post.getUserId()));
                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                items.add(PostListItemVO.of(post, author, likeCount));
            }
        }
        long total = result == null ? 0 : result.getTotalElements();
        int pages = result == null ? 0 : result.getTotalPages();
        return Result.ok(new PageResult<>(items, total, pageNum, pageSize, pages));
    }
}
