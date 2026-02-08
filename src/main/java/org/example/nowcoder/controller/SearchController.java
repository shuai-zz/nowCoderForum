package org.example.nowcoder.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.protocol.types.Field;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.Page;
import org.example.nowcoder.service.ElasticSearchService;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumConstant;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhaoshuai
 */
@Controller
@RequiredArgsConstructor
public class SearchController implements ForumConstant {
    private final ElasticSearchService elasticSearchService;
    private final UserService userService;
    private final LikeService likeService;

    // search?keyword=xxx
    @GetMapping("/search")
    public String search(String keyword, Page page, Model model) throws Exception {
        // 搜索帖子
        org.springframework.data.domain.Page<DiscussPost> searchResult =
                elasticSearchService.searchDiscussPost(keyword, page.getPageNum(), page.getPageSize());
        // 聚合数据
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (searchResult != null) {
            for (DiscussPost post : searchResult) {
                Map<String, Object> map = new HashMap<>();
                // 帖子
                map.put("post", post);
                // 作者
                map.put("user", userService.findUserById(post.getUserId()));
                // 点赞数量
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
                discussPosts.add(map);
            }
        }
        model.addAttribute("discussPosts", discussPosts);
        model.addAttribute("keyword", keyword);

        // 分页信息
        page.setPath("/search?keyword=" + keyword);
        page.setTotal(searchResult==null?0: (int) searchResult.getTotalElements());
        page.setPages(searchResult==null?0: searchResult.getTotalPages());
        // TODO: Navigate bar

        return "/site/search";

        }
    }
