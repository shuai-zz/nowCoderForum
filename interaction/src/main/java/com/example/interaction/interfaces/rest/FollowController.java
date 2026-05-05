package com.example.interaction.interfaces.rest;

import com.example.interaction.application.dto.FollowListItem;
import com.example.interaction.application.service.FollowService;
import com.example.interaction.interfaces.dto.FollowRequest;
import com.example.shared.exception.ResourceNotFoundException;
import com.example.shared.result.PageResult;
import com.example.shared.result.Result;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.domain.entity.Event;

import com.example.shared.messaging.EventProducer;
import com.example.interaction.interfaces.vo.FollowUserVO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;
import static com.example.shared.constant.ForumConstant.TOPIC_FOLLOW;


/**
 * @author zhaoshuai
 */
@Tag(name = "Follow", description = "关注 / 取关 / 关注列表 / 粉丝列表")
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final UserService userService;
    private final EventProducer eventProducer;

    @Operation(summary = "关注")
    @PostMapping("/api/v1/follows")
    public Result<Void> follow(@AuthenticationPrincipal User me, @Valid @RequestBody FollowRequest req) {
        followService.follow(me.getId(), req.entityType(), req.entityId());

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_FOLLOW)
                .setUserId(me.getId())
                .setEntityType(req.entityType())
                .setEntityId(req.entityId())
                .setEntityUserId(req.entityId()));
        return Result.ok();
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/api/v1/follows/{entityType}/{entityId}")
    public Result<Void> unfollow(@AuthenticationPrincipal User me, @PathVariable int entityType, @PathVariable int entityId) {
        followService.unfollow(me.getId(), entityType, entityId);
        return Result.ok();
    }

    @Operation(summary = "用户关注列表")
    @GetMapping("/api/v1/users/{userId}/followees")
    public Result<PageResult<FollowUserVO>> followees(
            @AuthenticationPrincipal User me,
            @PathVariable int userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        requireUserExists(userId);

        long total = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        List<FollowListItem> raw = followService.findFollowees(userId, pageNum, pageSize);
        List<FollowUserVO> items = toFollowUserVOList(me, raw);

        return Result.ok(buildPage(items, total, pageNum, pageSize));
    }

    @Operation(summary = "用户粉丝列表")
    @GetMapping("/api/v1/users/{userId}/followers")
    public Result<PageResult<FollowUserVO>> followers(
            @AuthenticationPrincipal User me,
            @PathVariable int userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        requireUserExists(userId);

        long total = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        List<FollowListItem> raw = followService.findFollowers(userId, pageNum, pageSize);
        List<FollowUserVO> items = toFollowUserVOList(me,raw);

        return Result.ok(buildPage(items, total, pageNum, pageSize));
    }

    // ---- helpers ----

    private void requireUserExists(int userId) {
        if (userService.getById(userId) == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    private List<FollowUserVO> toFollowUserVOList(User me, List<FollowListItem> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(item -> {
                    boolean hasFollowed = me!=null&&followService.hasFollowed(me.getId(), ENTITY_TYPE_USER, item.user().getId());
                    return new FollowUserVO(UserVO.from(item.user()), item.followTime(), hasFollowed);
                })
                .toList();

    }

    private <T> PageResult<T> buildPage(List<T> items, long total, int pageNum, int pageSize) {
        int pages = pageSize == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(items, total, pageNum, pageSize, pages);
    }
}
