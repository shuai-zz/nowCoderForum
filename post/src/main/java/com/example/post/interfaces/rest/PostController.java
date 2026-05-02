package com.example.post.interfaces.rest;

import com.example.interaction.application.service.CommentService;
import com.example.interaction.application.service.LikeService;
import com.example.interaction.domain.entity.Comment;
import com.example.interaction.interfaces.dto.CreatePostRequest;
import com.example.interaction.interfaces.vo.CommentVO;
import com.example.post.application.dto.PostListItem;
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
import java.util.Optional;
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
        PageData<PostListItem> pageData = discussPostService.selectDiscussPosts(pageNum, pageSize, uid);
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
    public Result<PostDetailVO> detail(@AuthenticationPrincipal User me,@PathVariable int id) {
        DiscussPost post = discussPostService.findDiscussPostById(id);
        if (post == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
        UserVO author = UserVO.from(userService.getById(post.getUserId()));
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, id);
        int likeStatus = currentUserLikeStatus(me, ENTITY_TYPE_POST, id);
        return Result.ok(PostDetailVO.of(post, author, likeCount, likeStatus));
    }

    @Operation(summary = "帖子评论（含一层回复）")
    @GetMapping("/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @AuthenticationPrincipal User me,
            @PathVariable int id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        if (discussPostService.findDiscussPostById(id) == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
        // 查询一级评论
        PageData<Comment> commentsPageData = commentService.findCommentsByEntity(ENTITY_TYPE_POST, id, pageNum, pageSize);
        // 若无评论
        if(commentsPageData.total() == 0){
            return Result.ok(PageResult.empty(pageNum, pageSize));
        }
        // 批量查询一级评论作者
        List<Integer> commentAuthIds = commentsPageData.items().stream()
                .map(Comment::getUserId)
                .distinct()
                .toList();
        Map<Integer, User> authorMap = userService.listByIds(commentAuthIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        // 所有comment以及reply
        List<CommentVO> commentsWithReplies = commentsPageData.items().stream()
                .map(comment -> {
                    UserVO commentAuthor = UserVO.from(authorMap.get(comment.getUserId()));
                    long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId());
                    int likeStatus = currentUserLikeStatus(me, ENTITY_TYPE_COMMENT, comment.getId());
                    // 获取reply
                    PageData<Comment> repliesPageData = commentService.findCommentsByEntity(ENTITY_TYPE_COMMENT, comment.getId(), 0, Integer.MAX_VALUE);
                    // 批量查询reply作者和reply的回复对象target
                    List<Integer> replyAuthIds = repliesPageData.items().stream()
                            .map(Comment::getUserId)
                            .distinct()
                            .toList();

                    List<Integer> targetUserIds = repliesPageData.items().stream()
                            .map(Comment::getTargetId)
                            .distinct()
                            .toList();

                    Map<Integer, User> replyAuthorMap = userService.listByIds(replyAuthIds).stream()
                            .collect(Collectors.toMap(User::getId, user -> user));
                    Map<Integer, User> replyTargetMap = userService.listByIds(targetUserIds).stream()
                            .collect(Collectors.toMap(User::getId, user -> user));
                    // 该comment下所有reply
                    List<ReplyVO> replies = repliesPageData.items().stream()
                            .map(reply->{
                                UserVO from = UserVO.from(replyAuthorMap.get(reply.getUserId()));
                                UserVO to = reply.getTargetId() == 0 ? null : UserVO.from(replyTargetMap.get(reply.getTargetId()));
                                long replyLikeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, reply.getId());
                                int replyLikeStatus = currentUserLikeStatus(me, ENTITY_TYPE_COMMENT, reply.getId());

                                return ReplyVO.of(reply, from, to, replyLikeCount, replyLikeStatus);
                            }).toList();
                    return CommentVO.of(comment, commentAuthor, likeCount, likeStatus, replies, repliesPageData.total());
                }).toList();

        // 最终结果
        return Result.ok(PageResult.of(commentsWithReplies, commentsPageData.total(), pageNum, pageSize));
    }

    @Operation(summary = "置顶（moderator）")
    @PatchMapping("/{id}/top")
    public Result<Void> top(@AuthenticationPrincipal User me, @PathVariable int id) {
        requirePostExists(id);
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
        requirePostExists(id);
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
        requirePostExists(id);
        discussPostService.updateStatus(id, POST_STATUS_DELETED);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        return Result.ok();
    }

    // ---- helpers ----

    private void requirePostExists(int id) {
        if (discussPostService.findDiscussPostById(id) == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
    }

    private int currentUserLikeStatus(User me, int entityType, int entityId) {
        return me == null ? 0 : likeService.findEntityLikeStatus(me.getId(), entityType, entityId);
    }
}
