package com.example.interaction.interfaces.rest;

import com.example.interaction.application.service.LikeService;
import com.example.interaction.interfaces.dto.LikeRequest;
import com.example.post.application.service.DiscussPostService;
import com.example.shared.result.Result;
import com.example.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.example.shared.messaging.Event;

import com.example.interaction.interfaces.vo.LikeStatusVO;
import com.example.shared.messaging.EventProducer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static com.example.shared.constant.ForumConstant.TOPIC_LIKE;


/**
 * @author zhaoshuai
 */
@Tag(name = "Like", description = "点赞 / 取消点赞（同接口 toggle）")
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final EventProducer eventProducer;
    private final DiscussPostService discussPostService;

    @Operation(summary = "对实体 toggle 点赞，返回最新计数与当前用户点赞状态")
    @PostMapping
    public Result<LikeStatusVO> toggle(@AuthenticationPrincipal User me, @Valid @RequestBody LikeRequest req) {

        int likeStatus = likeService.like(me.getId(), req.entityType(), req.entityId(), req.entityUserId());
        long likeCount = likeService.findEntityLikeCount(req.entityType(), req.entityId());

        // 仅在"新增点赞"时发 Kafka 通知
        if (likeStatus == 1) {
            eventProducer.fireEvent(new Event()
                    .setTopic(TOPIC_LIKE)
                    .setUserId(me.getId())
                    .setEntityType(req.entityType())
                    .setEntityId(req.entityId())
                    .setEntityUserId(req.entityUserId())
                    .setData("postId", req.postId()));
        }

        // 点赞发生在帖子上时刷新分数
        if (req.entityType() == ENTITY_TYPE_POST && req.postId() != null) {
            discussPostService.markForScoreRefresh(req.postId());
        }

        return Result.ok(new LikeStatusVO(likeCount, likeStatus));
    }
}
