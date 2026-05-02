package com.example.post.application.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.interaction.application.service.CommentService;
import com.example.interaction.application.service.LikeService;
import com.example.post.application.dto.PostListItem;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.post.infrastructure.mapper.DiscussPostMapper;
import com.example.shared.common.result.PageData;
import com.example.shared.common.utils.SensitiveFilter;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.shared.common.constant.ForumConstant.ENTITY_TYPE_POST;

/**
 * @author 23211
 */
@Service
@RequiredArgsConstructor
public class DiscussPostServiceImpl extends ServiceImpl<DiscussPostMapper, DiscussPost>
        implements DiscussPostService {
    private final SensitiveFilter sensitiveFilter;
    private final UserService userService;
    private final LikeService likeService;
    @Override
    public PageData<PostListItem> selectDiscussPosts(int pageNum, int pageSize, int userId) {
        Page<DiscussPost> page = new Page<>(pageNum, pageSize);
        List<DiscussPost> discussPosts = baseMapper.selectDiscussPosts(page, userId);
        List<Integer> ids = discussPosts.stream()
                .map(DiscussPost::getUserId)
                .toList();
        Map<Integer, User> userMap = userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<PostListItem> list = discussPosts.stream()
                .map(post -> {
                    User user = userMap.get(post.getUserId());
                    long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                    return new PostListItem(post, user, likeCount);
                })
                .toList();
        return new PageData<>(list, page.getTotal());
    }

    @Override
    public int insertDiscussPost(DiscussPost discussPost) {
        if(discussPost==null) {
            throw new IllegalArgumentException("post cannot be null");
        }
        // 转义HTML标记
        discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
        discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));

        // 过滤敏感词
        discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
        discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));


        return baseMapper.insert(discussPost);
    }

    @Override
    public DiscussPost findDiscussPostById(int discussPostId) {
        return baseMapper.selectById(discussPostId);
    }

    @Override
    public int updateCommentCount(int entityId, int count) {
        return baseMapper.updateCommentCount(entityId,count);
    }

    @Override
    public int updateType(int entityId, int type) {
        return baseMapper.updateType(entityId,type);
    }

    @Override
    public int updateStatus(int entityId, int status) {
        return baseMapper.updateStatus(entityId,status);
    }


    @Override
    public void updateScore(int postId, double score) {
        baseMapper.updateScore(postId,score);
    }
}
