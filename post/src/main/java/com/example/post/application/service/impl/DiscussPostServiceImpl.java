package com.example.post.application.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.post.application.dto.PostItem;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.post.infrastructure.mapper.DiscussPostMapper;
import com.example.shared.domain.ContentSanitizer;
import com.example.shared.result.PageData;
import com.example.shared.utils.RedisKeyUtil;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 23211
 */
@Service
@RequiredArgsConstructor
public class DiscussPostServiceImpl
        implements DiscussPostService {
    private final DiscussPostMapper discussPostMapper;
    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ContentSanitizer contentSanitizer;

    @Override
    public PageData<PostItem> selectDiscussPosts(int pageNum, int pageSize, int userId) {
        Page<DiscussPost> page = new Page<>(pageNum, pageSize);
        // 所有帖子
        List<DiscussPost> discussPosts = discussPostMapper.selectDiscussPosts(page, userId);
        // 获取所有帖子作者
        List<Integer> authIds = discussPosts.stream()
                .map(DiscussPost::getUserId)
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(authIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        // 获取所有帖子点赞数和当前登录用户点赞状态
        List<PostItem> list = discussPosts.stream()
                .map(post -> {
                    // TODO: 作者账号被删除，userMap.get()返回null， 可能有NPE问题
                    User user = userMap.get(post.getUserId());
                    return new PostItem(post, user, post.getLikeCount(), 0);
                })
                .toList();
        return new PageData<>(list, page.getTotal());
    }

    @Override
    public int insertDiscussPost(DiscussPost discussPost) {
        if (discussPost == null) {
            throw new IllegalArgumentException("post cannot be null");
        }
        // 转译HTML && 过滤敏感词
        String title = contentSanitizer.sanitize(discussPost.getTitle());
        String content = contentSanitizer.sanitize(discussPost.getContent());

        DiscussPost post = DiscussPost.builder()
                .userId(discussPost.getUserId())
                .title(title)
                .content(content)
                .type(discussPost.getType())
                .status(discussPost.getStatus())
                .createTime(discussPost.getCreateTime())
                .commentCount(discussPost.getCommentCount())
                .likeCount(discussPost.getLikeCount())
                .score(discussPost.getScore())
                .build();


        return discussPostMapper.insert(post);
    }

    @Override
    public void markAsTop(DiscussPost post) {
        post.markAsTop();
        discussPostMapper.updateById(post);
    }

    @Override
    public void markAsWonderful(DiscussPost post) {
        post.markAsWonderful();
        discussPostMapper.updateById(post);
    }

    @Override
    public void softDelete(DiscussPost post) {
        post.softDelete();
        discussPostMapper.updateById(post);
    }

    @Override
    public void refreshCommentCount(int postId, int count) {
        DiscussPost discussPost = discussPostMapper.selectById(postId);
        discussPost.refreshCommentCount(count);
        discussPostMapper.updateById(discussPost);
    }

    @Override
    public void updateScore(int postId, double score) {
        DiscussPost discussPost = discussPostMapper.selectById(postId);
        discussPost.updateScore(score);
        discussPostMapper.updateById(discussPost);
    }

    @Override
    public DiscussPost getRawPost(int postId) {
        return discussPostMapper.selectById(postId);
    }


    @Override
    public void markForScoreRefresh(int postId) {
        redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), postId);
    }

    @Override
    public PostItem findDiscussPostById(int discussPostId, int userId) {
        DiscussPost discussPost = discussPostMapper.selectById(discussPostId);
        User auth = userService.getById(discussPost.getUserId());
        return PostItem.of(discussPost, auth, discussPost.getLikeCount(), 0);
    }
}
