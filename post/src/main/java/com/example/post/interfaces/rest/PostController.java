package com.example.post.interfaces.rest;

import com.example.post.application.dto.PostItem;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.post.interfaces.dto.CreatePostRequest;
import com.example.shared.exception.ResourceNotFoundException;
import com.example.shared.result.PageData;
import com.example.shared.result.PageResult;
import com.example.shared.result.Result;
import com.example.user.domain.entity.User;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.example.post.interfaces.vo.PostDetailVO;
import com.example.post.interfaces.vo.PostListItemVO;
import com.example.shared.messaging.Event;
import com.example.shared.messaging.EventProducer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

import static com.example.shared.constant.ForumConstant.*;


/**
 * @author zhaoshuai
 */
@Tag(name = "Post", description = "帖子相关：列表 / 详情 / 发布 / 置顶 / 加精 / 删除")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private static final int ALL_USERS = 0;

    private final DiscussPostService discussPostService;
    private final EventProducer eventProducer;

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
        discussPostService.markForScoreRefresh(post.getId());

        return Result.ok(post.getId());
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/{id}")
    public Result<PostDetailVO> detail(@AuthenticationPrincipal User me, @PathVariable int id) {

        PostItem postItem = discussPostService.findDiscussPostById(id, me == null ? 0 : me.getId());
        if (postItem.discussPost() == null) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }

        return Result.ok(PostDetailVO.of(postItem.discussPost(), UserVO.from(postItem.author()), postItem.likeCount()));
    }

    @Operation(summary = "置顶（moderator）")
    @PatchMapping("/{id}/top")
    public Result<Void> top(@AuthenticationPrincipal User me, @PathVariable int id) {
        DiscussPost post = requirePostExists(id);
        discussPostService.markAsTop(post);

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
        DiscussPost post = requirePostExists(id);
        discussPostService.markAsWonderful(post);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        discussPostService.markForScoreRefresh(id);
        return Result.ok();
    }

    @Operation(summary = "删除（admin，软删）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal User me, @PathVariable int id) {
        DiscussPost post = requirePostExists(id);
        discussPostService.softDelete(post);

        eventProducer.fireEvent(new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(me.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id));
        return Result.ok();
    }

    // ---- helpers ----

    private DiscussPost requirePostExists(int id) {
        DiscussPost post = discussPostService.getRawPost(id);
        if (post == null || post.isDeleted()) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
        return post;
    }
}
