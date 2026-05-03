package com.example.interaction.interfaces.rest;

import com.example.interaction.application.service.CommentService;
import com.example.interaction.domain.entity.Comment;
import com.example.interaction.interfaces.dto.CreateCommentRequest;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.common.exception.ResourceNotFoundException;
import com.example.shared.common.result.Result;
import com.example.shared.common.utils.RedisKeyUtil;
import com.example.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.example.nowcoder.domain.entity.Event;
import org.example.nowcoder.infrastructure.messaging.EventProducer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

import static com.example.shared.common.constant.ForumConstant.*;


/**
 * @author zhaoshuai
 */
@Tag(name = "Comment", description = "评论 / 回复")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final DiscussPostService discussPostService;
    private final EventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "发布评论或回复")
    @PostMapping
    public Result<Integer> add(@AuthenticationPrincipal User me,  @Valid @RequestBody CreateCommentRequest req) {

        Comment c=Comment.builder()
                .userId(me.getId())
                .entityType(req.entityType())
                .entityId(req.entityId())
                .targetId(req.targetId() == null ? 0 : req.targetId())
                .content(req.content())
                .status(0)
                .createTime(new Date())
                .build();
        commentService.addComment(c);

        // 评论事件：构建通知
        Event commentEvent = new Event()
                .setTopic(TOPIC_COMMENT)
                .setUserId(me.getId())
                .setEntityType(req.entityType())
                .setEntityId(req.entityId())
                .setData("postId", req.postId());
        commentEvent.setEntityUserId(resolveTargetOwner(req.entityType(), req.entityId(), me.getId()));
        eventProducer.fireEvent(commentEvent);

        // 若是对帖子的一级评论：刷新帖子分数
        if (req.entityType() == ENTITY_TYPE_POST) {
            eventProducer.fireEvent(new Event()
                    .setTopic(TOPIC_PUBLISH)
                    .setUserId(me.getId())
                    .setEntityType(ENTITY_TYPE_POST)
                    .setEntityId(req.entityId()));
            redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), req.entityId());
        }

        return Result.ok(c.getId());
    }

    private int resolveTargetOwner(int entityType, int entityId, int currentUserId) {
        if (entityType == ENTITY_TYPE_POST) {
            DiscussPost p = discussPostService.findDiscussPostById(entityId, currentUserId).discussPost();
            if (p == null) {
                throw new ResourceNotFoundException("Post not found: " + entityId);
            }
            return p.getUserId();
        }
        if (entityType == ENTITY_TYPE_COMMENT) {
            Comment parent = commentService.findCommentById(entityId);
            if (parent == null) {
                throw new ResourceNotFoundException("Comment not found: " + entityId);
            }
            return parent.getUserId();
        }
        return 0;
    }
}
