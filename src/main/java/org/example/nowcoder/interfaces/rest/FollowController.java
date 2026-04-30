package org.example.nowcoder.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.domain.entity.Event;
import org.example.nowcoder.domain.entity.User;
import org.example.nowcoder.infrastructure.messaging.EventProducer;
import org.example.nowcoder.exception.ResourceNotFoundException;
import org.example.nowcoder.application.service.FollowService;
import org.example.nowcoder.application.service.UserService;
import org.example.nowcoder.infrastructure.util.HostHolder;
import org.example.nowcoder.interfaces.common.PageResult;
import org.example.nowcoder.interfaces.common.Result;
import org.example.nowcoder.interfaces.dto.FollowRequest;
import org.example.nowcoder.interfaces.vo.FollowUserVO;
import org.example.nowcoder.interfaces.vo.UserVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.example.nowcoder.infrastructure.util.ForumConstant.ENTITY_TYPE_USER;
import static org.example.nowcoder.infrastructure.util.ForumConstant.TOPIC_FOLLOW;

@Tag(name = "Follow", description = "关注 / 取关 / 关注列表 / 粉丝列表")
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final UserService userService;
    private final HostHolder hostHolder;
    private final EventProducer eventProducer;

    @Operation(summary = "关注")
    @PostMapping("/api/v1/follows")
    public Result<Void> follow(@Valid @RequestBody FollowRequest req) {
        User me = hostHolder.getUser();
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
    public Result<Void> unfollow(@PathVariable int entityType, @PathVariable int entityId) {
        User me = hostHolder.getUser();
        followService.unfollow(me.getId(), entityType, entityId);
        return Result.ok();
    }

    @Operation(summary = "用户关注列表")
    @GetMapping("/api/v1/users/{userId}/followees")
    public Result<PageResult<FollowUserVO>> followees(
            @PathVariable int userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        requireUserExists(userId);

        long total = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        List<Map<String, Object>> raw = followService.findFollowees(userId, pageNum, pageSize);
        List<FollowUserVO> items = toFollowUserVOList(raw);

        return Result.ok(buildPage(items, total, pageNum, pageSize));
    }

    @Operation(summary = "用户粉丝列表")
    @GetMapping("/api/v1/users/{userId}/followers")
    public Result<PageResult<FollowUserVO>> followers(
            @PathVariable int userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        requireUserExists(userId);

        long total = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        List<Map<String, Object>> raw = followService.findFollowers(userId, pageNum, pageSize);
        List<FollowUserVO> items = toFollowUserVOList(raw);

        return Result.ok(buildPage(items, total, pageNum, pageSize));
    }

    // ---- helpers ----

    private void requireUserExists(int userId) {
        if (userService.getById(userId) == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    private List<FollowUserVO> toFollowUserVOList(List<Map<String, Object>> raw) {
        List<FollowUserVO> items = new ArrayList<>();
        if (raw == null) return items;
        User me = hostHolder.getUser();
        for (Map<String, Object> m : raw) {
            User u = (User) m.get("user");
            if (u == null) continue;
            boolean hasFollowed = me != null && followService.hasFollowed(me.getId(), ENTITY_TYPE_USER, u.getId());
            items.add(new FollowUserVO(UserVO.from(u), (Date) m.get("followTime"), hasFollowed));
        }
        return items;
    }

    private <T> PageResult<T> buildPage(List<T> items, long total, int pageNum, int pageSize) {
        int pages = pageSize == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(items, total, pageNum, pageSize, pages);
    }
}
