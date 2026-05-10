package com.example.interaction.interfaces.rest;

import com.example.interaction.application.dto.CommentWithLike;
import com.example.interaction.application.service.CommentService;
import com.example.interaction.interfaces.vo.CommentVO;
import com.example.interaction.interfaces.vo.ReplyVO;
import com.example.post.application.service.DiscussPostService;
import com.example.shared.exception.ResourceNotFoundException;
import com.example.shared.result.PageData;
import com.example.shared.result.PageResult;
import com.example.shared.result.Result;
import com.example.user.application.service.UserService;
import com.example.user.domain.entity.User;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.shared.constant.ForumConstant.*;

@Tag(name = "PostComment", description = "帖子评论查询")
@RestController
@RequiredArgsConstructor
public class PostCommentController {

    private final CommentService commentService;
    private final DiscussPostService discussPostService;
    private final UserService userService;

    @Operation(summary = "帖子评论（含一层回复）")
    @GetMapping("/api/v1/posts/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @AuthenticationPrincipal User me,
            @PathVariable int id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        int currentUserId = me == null ? 0 : me.getId();
        requirePostExists(id, currentUserId);
        // 一级评论
        PageData<CommentWithLike> pageData = commentService.findCommentsWithLike(ENTITY_TYPE_POST, id, pageNum, pageSize, currentUserId);
        if (pageData.total() == 0) {
            return Result.ok(PageResult.empty(pageNum, pageSize));
        }

        // 1次窗口函数查询：所有以及评论的top-K回复
        List<Integer> parentIds = pageData.items().stream()
                .map(cwl -> cwl.comment().getId()).toList();
        Map<Integer, List<CommentWithLike>> repliesByParent = commentService.findTopRepliesGrouped(parentIds, REPLY_PREVIEW_LIMIT, currentUserId);

        // 1次 user 查询：一级作者 + reply作者 + reply target
        List<Integer> userIds = Stream.of(
                        pageData.items().stream().map(cwl -> cwl.comment().getUserId()),
                        repliesByParent.values().stream().flatMap(List::stream)
                                .map(r -> r.comment().getUserId()),
                        repliesByParent.values().stream().flatMap(List::stream)
                                .map(r -> r.comment().getTargetId())
                                .filter(targetId -> targetId != 0)
                )
                .flatMap(s -> s)
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<CommentVO> list = pageData.items().stream()
                .map(cwl -> buildCommentVo(cwl, repliesByParent, userMap))
                .toList();
        return Result.ok(PageResult.of(list, pageData.total(), pageNum, pageSize));
    }

    private void requirePostExists(int id, int currentUserId) {
        if (discussPostService.getRawPost(id) == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
    }

    private CommentVO buildCommentVo(CommentWithLike commentWithLike,
                                     Map<Integer, List<CommentWithLike>> repliesByParent,
                                     Map<Integer, User> userMap) {
        UserVO author = UserVO.from(userMap.get(commentWithLike.comment().getUserId()));
        List<ReplyVO> replies = repliesByParent.getOrDefault(commentWithLike.comment().getId(), List.of()).stream()
                .map(r -> ReplyVO.of(
                        r.comment(),
                        UserVO.from(userMap.get(r.comment().getUserId())),
                        UserVO.from(userMap.get(r.comment().getTargetId())),
                        r.likeCount(),
                        r.likeStatus()
                )).toList();
        return CommentVO.of(
                commentWithLike.comment(),
                author,
                commentWithLike.likeCount()
                , commentWithLike.likeStatus(),
                replies,
                commentWithLike.comment().getReplyCount()
        );
    }

    @Operation(summary = "评论的回复分页")
    @GetMapping("/api/v1/comments/{id}/replies")
    public Result<PageResult<ReplyVO>> replies(
            @AuthenticationPrincipal User me,
            @PathVariable int id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        int currentUserId = me == null ? 0 : me.getId();
        PageData<CommentWithLike> pageData =
                commentService.findCommentsWithLike(ENTITY_TYPE_COMMENT, id, pageNum, pageSize, currentUserId);
        if (pageData.total() == 0) {
            return Result.ok(PageResult.empty(pageNum, pageSize));
        }

        List<Integer> userIds = pageData.items().stream()
                .flatMap(cwl -> Stream.of(cwl.comment().getUserId(), cwl.comment().getTargetId()))
                .filter(uid -> uid != 0)
                .distinct()
                .toList();
        Map<Integer, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ReplyVO> list = pageData.items().stream()
                .map(r -> ReplyVO.of(
                        r.comment(),
                        UserVO.from(userMap.get(r.comment().getUserId())),
                        UserVO.from(userMap.get(r.comment().getTargetId())),
                        r.likeCount(),
                        r.likeStatus()))
                .toList();
        return Result.ok(PageResult.of(list, pageData.total(), pageNum, pageSize));
    }

}
