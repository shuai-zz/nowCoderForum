package com.example.interaction.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.interaction.application.dto.CommentWithLike;
import com.example.interaction.application.service.CommentService;
import com.example.interaction.application.service.LikeService;
import com.example.interaction.domain.entity.Comment;
import com.example.interaction.infrastructure.mapper.CommentMapper;
import com.example.post.application.service.DiscussPostService;
import com.example.shared.common.constant.ForumConstant;
import com.example.shared.common.result.PageData;
import com.example.shared.common.utils.SensitiveFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

import static com.example.shared.common.constant.ForumConstant.ENTITY_TYPE_COMMENT;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final SensitiveFilter sensitiveFilter;
    private final DiscussPostService discussPostService;
    private final LikeService likeService;

    @Override
    public PageData<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        List<Comment> list = baseMapper.selectCommentsByEntity(page, entityType, entityId);
        return new PageData<>(list, page.getTotal());
    }

    @Override
    public int findCommentCount(int entityType, int entityId) {
        return baseMapper.selectCount(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getEntityType, entityType)
                .eq(Comment::getEntityId, entityId)
                .eq(Comment::getStatus, 0)).intValue();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    @Override
    public int addComment(Comment comment) {
        if (comment == null) {
            throw new IllegalArgumentException("parameter cannot be null");
        }
        comment.setContent(HtmlUtils.htmlEscape(comment.getContent()));
        comment.setContent(sensitiveFilter.filter(comment.getContent()));
        int rows = baseMapper.insert(comment);

        if (comment.getEntityType() == ForumConstant.ENTITY_TYPE_POST) {
            int count = findCommentCount(comment.getEntityType(), comment.getEntityId());
            discussPostService.updateCommentCount(comment.getEntityId(), count);
        }
        return rows;
    }

    @Override
    public Comment findCommentById(int id) {
        return baseMapper.selectById(id);
    }


    @Override
    public PageData<CommentWithLike> findCommentsWithLike(int entityType, int entityId, int pageNum, int pageSize, int currentUserId) {
        Page<Comment> page=new Page<>(pageNum, pageSize);
        List<Comment> comments = baseMapper.selectCommentsByEntity(page, entityType, entityId);
        if(comments.isEmpty()){
            return new PageData<>(List.of(), 0);
        }
        List<CommentWithLike> list = comments.stream()
                .map(comment -> {
                    long likeCount = likeService.findEntityLikeCount(entityType, comment.getId());
                    int likeStatus = currentUserId == 0 ? 0 : likeService.findEntityLikeStatus(currentUserId, entityType, comment.getId());
                    return CommentWithLike.of(comment, likeCount, likeStatus);
                })
                .toList();

        return new PageData<>(list, page.getTotal());
    }
}