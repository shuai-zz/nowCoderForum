package com.example.post.application.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.interaction.application.service.LikeService;
import com.example.post.application.dto.PostItem;
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
    public PageData<PostItem> selectDiscussPosts(int pageNum, int pageSize, int userId) {
        Page<DiscussPost> page = new Page<>(pageNum, pageSize);
        // 所有帖子
        List<DiscussPost> discussPosts = baseMapper.selectDiscussPosts(page, userId);
        // 获取所有帖子作者
        List<Integer> authIds = discussPosts.stream()
                .map(DiscussPost::getUserId)
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(authIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        // 获取所有帖子点赞数和当前登录用户点赞状态
        List<Integer> postIds = discussPosts.stream()
                .map(DiscussPost::getId)
                .toList();
        Map<Integer, Long> likeCountMap = likeService.findEntityLikeCounts(ENTITY_TYPE_POST, postIds);

        List<PostItem> list = discussPosts.stream()
                .map(post -> {
                    // TODO: 作者账号被删除，userMap.get()返回null， 可能有NPE问题
                    User user = userMap.get(post.getUserId());
                    Long likeCount = likeCountMap.get(post.getId());
                    return new PostItem(post, user, likeCount,0);
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

    @Override
    public PostItem findDiscussPostById(int discussPostId, int userId) {
        DiscussPost discussPost = baseMapper.selectById(discussPostId);
        User auth = userService.getById(discussPost.getUserId());
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, discussPostId);
        int likeStatus = likeService.findEntityLikeStatus(userId, ENTITY_TYPE_POST, discussPostId);
        return PostItem.of(discussPost, auth, likeCount, likeStatus);
    }
}
