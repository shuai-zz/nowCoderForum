package com.example.post.interfaces.rest;

import com.example.interaction.application.dto.CommentWithLike;
import com.example.interaction.application.service.CommentService;
import com.example.interaction.application.service.LikeService;
import com.example.interaction.interfaces.dto.CreatePostRequest;
import com.example.interaction.interfaces.vo.CommentVO;
import com.example.post.application.dto.PostItem;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.common.exception.ResourceNotFoundException;
import com.example.shared.common.result.PageData;
import com.example.shared.common.result.PageResult;
import com.example.shared.common.result.Result;
import com.example.shared.common.utils.RedisKeyUtil;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.example.post.interfaces.vo.PostDetailVO;
import com.example.post.interfaces.vo.PostListItemVO;
import org.example.nowcoder.domain.entity.Event;
import org.example.nowcoder.infrastructure.messaging.EventProducer;
import org.example.nowcoder.interfaces.vo.ReplyVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.shared.common.constant.ForumConstant.*;


/**
 * @author zhaoshuai
 */
@Tag(name = "Post", description = "帖子相关：列表 / 详情 / 发布 / 置顶 / 加精 / 删除 / 评论")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private static final int ALL_USERS = 0;
    private static final int POST_TYPE_TOP = 1;
    private static final int POST_STATUS_WONDERFUL = 1;
    private static final int POST_STATUS_DELETED = 2;

    private final DiscussPostService discussPostService;
    private final CommentService commentService;
    private final UserService userService;
    private final LikeService likeService;
    private final EventProducer eventProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "帖子列表（可选按作者筛选）")
    @GetMapping
    public Result<PageResult<PostListItemVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer userId
    ) {
        int uid = userId == null ? ALL_USERS : userId;
        PageData<PostItem> pageData = discussPostService.selectDiscussPosts(pageNum, pageSize, uid);
        List<PostListItemVO> list = pageData.items().stream()
                .map(item -> {
                    UserVO userVo = UserVO.from(item.author());
                    return PostListItemVO.of(item.discussPost(), userVo, item.likeCount());
                })
                .toList();

        return Result.ok(PageResult.of(list, pageData.total(), pageNum, pageSize));
    }


    @Operation(summary = "发布帖子")
    @PostMapping
    public Result<Integer> create(@AuthenticationPrincipal User me, @Valid @RequestBody CreatePostRequest req) {
        DiscussPost post = DiscussPost.builder()
                .userId(me.getId())
                .title(req.title())
                .content(req.content())
                .createTime(new Date())
                .build();
        discussPostService.insertDiscussPost(post);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(post.getId()));
        redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), post.getId());

        return Result.ok(post.getId());
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/{id}")
    public Result<PostDetailVO> detail(@AuthenticationPrincipal User me, @PathVariable int id) {

        PostItem postItem = discussPostService.findDiscussPostById(id, me == null ? 0 : me.getId());
        if (postItem.discussPost() == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }

        return Result.ok(PostDetailVO.of(postItem.discussPost(), UserVO.from(postItem.author()), postItem.likeCount(), postItem.likeStatus()));
    }

    @Operation(summary = "帖子评论（含一层回复）")
    @GetMapping("/{id}/comments")
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


    @Operation(summary = "置顶（moderator）")
    @PatchMapping("/{id}/top")
    public Result<Void> top(@AuthenticationPrincipal User me, @PathVariable int id) {
        requirePostExists(id, me.getId());
        discussPostService.updateType(id, POST_TYPE_TOP);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        return Result.ok();
    }

    @Operation(summary = "加精（moderator）")
    @PatchMapping("/{id}/wonderful")
    public Result<Void> wonderful(@AuthenticationPrincipal User me, @PathVariable int id) {
        requirePostExists(id, me.getId());
        discussPostService.updateStatus(id, POST_STATUS_WONDERFUL);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), id);
        return Result.ok();
    }

    @Operation(summary = "删除（admin，软删）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal User me, @PathVariable int id) {
        requirePostExists(id, me==null?0:me.getId());
        discussPostService.updateStatus(id, POST_STATUS_DELETED);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        return Result.ok();
    }

    // ---- helpers ----

    private void requirePostExists(int id, int currentUserId) {
        if (discussPostService.findDiscussPostById(id, currentUserId).discussPost() == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
    }

    private int currentUserLikeStatus(User me, int entityType, int entityId) {
        return me == null ? 0 : likeService.findEntityLikeStatus(me.getId(), entityType, entityId);
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
