package com.example.search.application.service.impl;

import com.example.interaction.application.service.LikeService;
import com.example.post.application.dto.PostItem;
import com.example.post.domain.entity.DiscussPost;
import com.example.search.application.service.ElasticSearchService;
import com.example.search.infrastructure.mapper.DiscussPostRepository;
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

    private final DiscussPostRepository discussPostRepository;
    private final UserService userService;
    private final LikeService likeService;

    @Override
    public void saveDiscussPost(DiscussPost discussPost) {
        discussPostRepository.save(discussPost);
    }

    @Override
    public void deleteDiscussPost(int id) {
        discussPostRepository.deleteById(id);
    }

    @Override
    public PageData<PostItem> searchDiscussPost(String keyWord, int pageNum, int pageSize) {
        PageData<DiscussPost> page = discussPostRepository.searchByKeyword(keyWord, pageNum, pageSize);
        List<DiscussPost> posts = page.items();

        List<Integer> authIds = posts.stream()
                .map(DiscussPost::getUserId)
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(authIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Integer> postIds = posts.stream()
                .map(DiscussPost::getId)
                .toList();
        Map<Integer, Long> likeCountMap = likeService.findEntityLikeCounts(ENTITY_TYPE_POST, postIds);

        List<PostItem> items = posts.stream()
                .map(p -> new PostItem(
                        p,
                        userMap.get(p.getUserId()),
                        likeCountMap.getOrDefault(p.getId(), 0L),
                        0
                ))
                .toList();
        return new PageData<>(items, page.total());
    }
}