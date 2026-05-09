package com.example.search.application.service.impl;

import com.example.interaction.application.service.LikeService;
import com.example.post.domain.entity.DiscussPost;
import com.example.search.application.service.ElasticSearchService;
import com.example.search.domain.SearchResult;
import com.example.search.domain.SearchablePost;
import com.example.search.infrastructure.mapper.SearchablePostRepository;
import com.example.shared.dto.AuthorRef;
import com.example.shared.result.PageData;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class ElasticSearchServiceImpl implements ElasticSearchService {

    private final SearchablePostRepository searchablePostRepository;
    private final UserService userService;
    private final LikeService likeService;

    @Override
    public void saveDiscussPost(DiscussPost discussPost) {
        searchablePostRepository.save(SearchablePost.from(discussPost));
    }

    @Override
    public void deleteDiscussPost(int id) {
        searchablePostRepository.deleteById(id);
    }

    @Override
    public PageData<SearchResult> searchDiscussPost(String keyWord, int pageNum, int pageSize) {
        PageData<SearchResult> pageData = searchablePostRepository.searchByKeyword(keyWord, pageNum, pageSize);
        List<SearchResult> searchResults = pageData.items();

        // 批量查作者信息
        List<Integer> authIds = searchResults.stream()
                .map(SearchResult::userId)
                .distinct()
                .toList();
        Map<Integer, AuthorRef> authorMap = userService.listByIds(authIds).stream()
                .collect(Collectors.toMap(User::getId, u -> AuthorRef.of(u.getId(), u.getUsername(), u.getAvatarUrl())));

        // 批量查实时点赞数
        List<Integer> postIds = searchResults.stream()
                .map(SearchResult::id)
                .toList();
        Map<Integer, Long> likeCountMap = likeService.findEntityLikeCounts(ENTITY_TYPE_POST, postIds);

        // 组装完整 SearchResult（填充 author + 实时 likeCount）
        List<SearchResult> items = searchResults.stream()
                .map(item -> SearchResult.of(
                        item.id(),
                        item.userId(),
                        authorMap.get(item.userId()),
                        item.title(),
                        item.content(),
                        item.highlightTitle(),
                        item.highlightContent(),
                        item.type(),
                        item.status(),
                        item.commentCount(),
                        likeCountMap.getOrDefault(item.id(), item.likeCount()),
                        item.score(),
                        item.createTime()
                ))
                .toList();
        return new PageData<>(items, pageData.total());
    }
}
