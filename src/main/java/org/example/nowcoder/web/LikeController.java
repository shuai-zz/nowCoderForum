package org.example.nowcoder.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Event;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.event.EventProducer;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.utils.HostHolder;
import org.example.nowcoder.utils.RedisKeyUtil;
import org.example.nowcoder.web.common.Result;
import org.example.nowcoder.web.dto.LikeRequest;
import org.example.nowcoder.web.vo.LikeStatusVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.example.nowcoder.utils.ForumConstant.ENTITY_TYPE_POST;
import static org.example.nowcoder.utils.ForumConstant.TOPIC_LIKE;

@Tag(name = "Like", description = "点赞 / 取消点赞（同接口 toggle）")
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final HostHolder hostHolder;
    private final EventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "对实体 toggle 点赞，返回最新计数与当前用户点赞状态")
    @PostMapping
    public Result<LikeStatusVO> toggle(@Valid @RequestBody LikeRequest req) {
        User me = hostHolder.getUser();

        likeService.like(me.getId(), req.entityType(), req.entityId(), req.entityUserId());

        long likeCount = likeService.findEntityLikeCount(req.entityType(), req.entityId());
        int likeStatus = likeService.findEntityLikeStatus(me.getId(), req.entityType(), req.entityId());

        // 仅在"新增点赞"时发通知
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
            redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), req.postId());
        }

        return Result.ok(new LikeStatusVO(likeCount, likeStatus));
    }
}
