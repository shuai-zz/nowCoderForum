package org.example.nowcoder.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Comment;
import org.example.nowcoder.entity.DiscussPost;
import org.example.nowcoder.entity.Event;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.event.EventProducer;
import org.example.nowcoder.exception.ResourceNotFoundException;
import org.example.nowcoder.service.CommentService;
import org.example.nowcoder.service.DiscussPostService;
import org.example.nowcoder.utils.HostHolder;
import org.example.nowcoder.utils.RedisKeyUtil;
import org.example.nowcoder.controller.common.Result;
import org.example.nowcoder.entity.dto.CreateCommentRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

import static org.example.nowcoder.utils.ForumConstant.*;

@Tag(name = "Comment", description = "评论 / 回复")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final DiscussPostService discussPostService;
    private final HostHolder hostHolder;
    private final EventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "发布评论或回复")
    @PostMapping
    public Result<Integer> add(@Valid @RequestBody CreateCommentRequest req) {
        User me = hostHolder.getUser();

        Comment c = new Comment();
        c.setUserId(me.getId());
        c.setEntityType(req.entityType());
        c.setEntityId(req.entityId());
        c.setTargetId(req.targetId() == null ? 0 : req.targetId());
        c.setContent(req.content());
        c.setStatus(0);
        c.setCreateTime(new Date());
        commentService.addComment(c);

        // 评论事件：构建通知
        Event commentEvent = new Event()
                .setTopic(TOPIC_COMMENT)
                .setUserId(me.getId())
                .setEntityType(req.entityType())
                .setEntityId(req.entityId())
                .setData("postId", req.postId());
        commentEvent.setEntityUserId(resolveTargetOwner(req.entityType(), req.entityId()));
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

    private int resolveTargetOwner(int entityType, int entityId) {
        if (entityType == ENTITY_TYPE_POST) {
            DiscussPost p = discussPostService.findDiscussPostById(entityId);
            if (p == null) throw new ResourceNotFoundException("Post not found: " + entityId);
            return p.getUserId();
        }
        if (entityType == ENTITY_TYPE_COMMENT) {
            Comment parent = commentService.findCommentById(entityId);
            if (parent == null) throw new ResourceNotFoundException("Comment not found: " + entityId);
            return parent.getUserId();
        }
        return 0;
    }
}
