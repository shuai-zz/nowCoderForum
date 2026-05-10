package com.example.interaction.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interaction.application.dto.CommentWithLike;
import com.example.interaction.application.service.CommentService;
import com.example.interaction.application.service.EntityExistenceChecker;
import com.example.interaction.application.service.LikeService;
import com.example.interaction.domain.entity.Comment;
import com.example.interaction.infrastructure.mapper.CommentMapper;
import com.example.post.application.service.DiscussPostService;
import com.example.shared.domain.ContentSanitizer;
import com.example.shared.result.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private final CommentMapper commentMapper;
    private final DiscussPostService discussPostService;
    private final LikeService likeService;
    private final ContentSanitizer contentSanitizer;
    private final EntityExistenceChecker entityExistenceChecker;

    @Override
    public PageData<Comment> findCommentsByEntity(int entityType, int entityId, int pageNum, int pageSize) {
        Page<Comment> page = new Page<>(pageNum, pageSize);
        List<Comment> list = commentMapper.selectCommentsByEntity(page, entityType, entityId);
        return new PageData<>(list, page.getTotal());
    }

    @Override
    public int findCommentCount(int entityType, int entityId) {
        return commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
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
        entityExistenceChecker.requireExists(comment.getEntityType(), comment.getEntityId());
        comment.applySanitizedContent(contentSanitizer.sanitize(comment.getContent()));

        int rows = commentMapper.insert(comment);

        // comment数
        if (comment.isOnPost()) {
            int count = findCommentCount(comment.getEntityType(), comment.getEntityId());
            discussPostService.refreshCommentCount(comment.getEntityId(), count);
        }
        // reply 数
        if (comment.isReply()){
            int count = findCommentCount(comment.getEntityType(), comment.getEntityId());
            commentMapper.refreshReplyCount(comment.getEntityId(), count);
        }

        return rows;
    }

    @Override
    public Comment findCommentById(int id) {
        return commentMapper.selectById(id);
    }


    @Override
    public PageData<CommentWithLike> findCommentsWithLike(int entityType, int entityId, int pageNum, int pageSize, int currentUserId) {
        Page<Comment> page=new Page<>(pageNum, pageSize);
        // 所有评论
        List<Comment> comments = commentMapper.selectCommentsByEntity(page, entityType, entityId);
        if(comments.isEmpty()){
            return new PageData<>(List.of(), 0);
        }
        // likeCount 直接来自评论本地字段（CommentLikeEventListener 维护，与 post 对称）
        // likeStatus 是当前用户的交互状态，仍需走 Redis 批量查询
        List<Integer> commentIds = comments.stream()
                .map(Comment::getId)
                .toList();
        Map<Integer, Integer> likeStatusMap = likeService.findEntityLikeStatuses(currentUserId, ENTITY_TYPE_COMMENT, commentIds);

        List<CommentWithLike> list = comments.stream()
                .map(comment -> CommentWithLike.of(comment, comment.getLikeCount(), likeStatusMap.get(comment.getId())))
                .toList();

        return new PageData<>(list, page.getTotal());
    }

    @Override
    public Map<Integer, List<CommentWithLike>> findTopRepliesGrouped(List<Integer> parentIds, int limit, int currentUserId) {
        if(parentIds.isEmpty()){
            return Map.of();
        }
        List<Comment> replies = commentMapper.selectTopRepliesGrouped(parentIds, limit);
        if(replies.isEmpty()){
            return Map.of();
        }

        // 一次性查所有的 reply 的 likeStatus
        List<Integer> replyIds = replies.stream()
                .map(Comment::getId).toList();
        Map<Integer, Integer> likeStatusMap = likeService.findEntityLikeStatuses(currentUserId, ENTITY_TYPE_COMMENT, replyIds);

        return replies.stream()
                .map(c->CommentWithLike.of(c, c.getLikeCount(), likeStatusMap.get(c.getId())))
                .collect(Collectors.groupingBy(cwl->cwl.comment().getEntityId()));
    }
}