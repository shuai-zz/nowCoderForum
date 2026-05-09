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

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;

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
        // 一级comment作者Map
        List<Integer> authorIds = pageData.items().stream()
                .map(commentWithLike ->
                        commentWithLike.comment().getUserId()
                )
                .distinct()
                .toList();
        Map<Integer, User> authorMap = userService.listByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<CommentVO> list = pageData.items().stream()
                .map(commentWithLike -> buildCommentVo(commentWithLike, authorMap, me))
                .toList();
        return Result.ok(PageResult.of(list, pageData.total(), pageNum, pageSize));
    }

    private void requirePostExists(int id, int currentUserId) {
        if (discussPostService.getRawPost(id) == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
    }

    private CommentVO buildCommentVo(CommentWithLike commentWithLike, Map<Integer, User> authorMap, User me) {
        UserVO author = UserVO.from(authorMap.get(commentWithLike.comment().getUserId()));
        // 查询该comment下所有reply
        PageData<CommentWithLike> pageData = commentService.findCommentsWithLike(ENTITY_TYPE_COMMENT, commentWithLike.comment().getId(), 0, Integer.MAX_VALUE, me == null ? 0 : me.getId());
        // 缓存reply的author和reply的target
        List<Integer> authorReplyIds = pageData.items().stream()
                .map(replyWithLike ->
                        replyWithLike.comment().getUserId()
                )
                .distinct().toList();
        List<Integer> targetReplyIds = pageData.items().stream()
                .map(replyWithLike -> replyWithLike.comment().getTargetId())
                .filter(targetId->targetId!=0)
                .distinct().toList();
        Map<Integer, User> authorReplyMap = userService.listByIds(authorReplyIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<Integer, User> targetReplyMap = userService.listByIds(targetReplyIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        // 构建replyVO
        List<ReplyVO> replies = pageData.items().stream()
                .map(replyWithLike -> {
                    UserVO from = UserVO.from(authorReplyMap.get(replyWithLike.comment().getUserId()));
                    UserVO to = UserVO.from(targetReplyMap.get(replyWithLike.comment().getTargetId()));
                    return ReplyVO.of(replyWithLike.comment(), from, to, replyWithLike.likeCount(), replyWithLike.likeStatus());
                })
                .toList();

        return CommentVO.of(commentWithLike.comment(), author, commentWithLike.likeCount(), commentWithLike.likeStatus(),
               replies, pageData.total());
    }
}
