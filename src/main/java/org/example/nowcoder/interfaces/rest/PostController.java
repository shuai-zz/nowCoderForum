package org.example.nowcoder.interfaces.rest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.domain.entity.Comment;
import org.example.nowcoder.domain.entity.DiscussPost;
import org.example.nowcoder.domain.entity.Event;
import org.example.nowcoder.domain.entity.User;
import org.example.nowcoder.infrastructure.messaging.EventProducer;
import org.example.nowcoder.exception.ResourceNotFoundException;
import org.example.nowcoder.application.service.CommentService;
import org.example.nowcoder.application.service.DiscussPostService;
import org.example.nowcoder.application.service.LikeService;
import org.example.nowcoder.application.service.UserService;
import org.example.nowcoder.infrastructure.util.HostHolder;
import org.example.nowcoder.infrastructure.util.RedisKeyUtil;
import org.example.nowcoder.interfaces.common.PageResult;
import org.example.nowcoder.interfaces.common.Result;
import org.example.nowcoder.interfaces.dto.CreatePostRequest;
import org.example.nowcoder.interfaces.vo.CommentVO;
import org.example.nowcoder.interfaces.vo.PostDetailVO;
import org.example.nowcoder.interfaces.vo.PostListItemVO;
import org.example.nowcoder.interfaces.vo.ReplyVO;
import org.example.nowcoder.interfaces.vo.UserVO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.example.nowcoder.infrastructure.util.ForumConstant.*;

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
    private final HostHolder hostHolder;
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
        PageInfo<DiscussPost> page = discussPostService.selectDiscussPosts(uid, pageNum, pageSize);

        List<PostListItemVO> items = new ArrayList<>();
        if (page.getList() != null) {
            for (DiscussPost post : page.getList()) {
                UserVO author = UserVO.from(userService.getById(post.getUserId()));
                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                items.add(PostListItemVO.of(post, author, likeCount));
            }
        }
        return Result.ok(new PageResult<>(
                items, page.getTotal(), page.getPageNum(), page.getPageSize(), page.getPages()));
    }

    @Operation(summary = "发布帖子")
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody CreatePostRequest req) {
        User me = hostHolder.getUser();
        DiscussPost post = new DiscussPost();
        post.setUserId(me.getId());
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setCreateTime(new Date());
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
    public Result<PostDetailVO> detail(@PathVariable int id) {
        DiscussPost post = discussPostService.findDiscussPostById(id);
        if (post == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
        UserVO author = UserVO.from(userService.getById(post.getUserId()));
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, id);
        int likeStatus = currentUserLikeStatus(ENTITY_TYPE_POST, id);
        return Result.ok(PostDetailVO.of(post, author, likeCount, likeStatus));
    }

    @Operation(summary = "帖子评论（含一层回复）")
    @GetMapping("/{id}/comments")
    public Result<PageResult<CommentVO>> comments(
            @PathVariable int id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "5") int pageSize
    ) {
        if (discussPostService.findDiscussPostById(id) == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
        Page<Comment> page = commentService.findCommentsByEntity(ENTITY_TYPE_POST, id, pageNum, pageSize);

        List<CommentVO> vos = new ArrayList<>();
        if (page.getRecords() != null) {
            for (Comment c : page.getRecords()) {
                vos.add(buildCommentVO(c));
            }
        }
        return Result.ok(new PageResult<>(
                vos, page.getTotal(), (int) page.getCurrent(), (int) page.getSize(), (int) page.getPages()));
    }

    @Operation(summary = "置顶（moderator）")
    @PatchMapping("/{id}/top")
    public Result<Void> top(@PathVariable int id) {
        requirePostExists(id);
        discussPostService.updateType(id, POST_TYPE_TOP);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        return Result.ok();
    }

    @Operation(summary = "加精（moderator）")
    @PatchMapping("/{id}/wonderful")
    public Result<Void> wonderful(@PathVariable int id) {
        requirePostExists(id);
        discussPostService.updateStatus(id, POST_STATUS_WONDERFUL);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), id);
        return Result.ok();
    }

    @Operation(summary = "删除（admin，软删）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable int id) {
        requirePostExists(id);
        discussPostService.updateStatus(id, POST_STATUS_DELETED);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(hostHolder.getUser().getId())
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

    private int currentUserLikeStatus(int entityType, int entityId) {
        User me = hostHolder.getUser();
        return me == null ? 0 : likeService.findEntityLikeStatus(me.getId(), entityType, entityId);
    }

    private CommentVO buildCommentVO(Comment c) {
        UserVO author = UserVO.from(userService.getById(c.getUserId()));
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, c.getId());
        int likeStatus = currentUserLikeStatus(ENTITY_TYPE_COMMENT, c.getId());

        Page<Comment> repliesPage = commentService.findCommentsByEntity(
                ENTITY_TYPE_COMMENT, c.getId(), 0, Integer.MAX_VALUE);
        List<ReplyVO> replies = new ArrayList<>();
        if (repliesPage != null && repliesPage.getRecords() != null) {
            for (Comment r : repliesPage.getRecords()) {
                replies.add(buildReplyVO(r));
            }
        }
        long replyCount = repliesPage == null ? 0 : repliesPage.getTotal();

        return CommentVO.of(c, author, likeCount, likeStatus, replies, replyCount);
    }

    private ReplyVO buildReplyVO(Comment r) {
        UserVO rAuthor = UserVO.from(userService.getById(r.getUserId()));
        UserVO target = r.getTargetId() == 0
                ? null
                : UserVO.from(userService.getById(r.getTargetId()));
        long rLikeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, r.getId());
        int rLikeStatus = currentUserLikeStatus(ENTITY_TYPE_COMMENT, r.getId());
        return ReplyVO.of(r, rAuthor, target, rLikeCount, rLikeStatus);
    }
}
