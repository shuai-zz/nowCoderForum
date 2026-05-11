package com.example.system.seed;

import com.example.interaction.application.service.CommentService;
import com.example.interaction.application.service.FollowService;
import com.example.interaction.application.service.LikeService;
import com.example.interaction.domain.entity.Comment;
import com.example.message.application.service.MessageService;
import com.example.message.domain.entity.Message;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.shared.messaging.Event;
import com.example.shared.messaging.EventProducer;
import com.example.user.domain.entity.User;
import com.example.user.domain.entity.UserStatistics;
import com.example.user.infrastructure.mapper.UserMapper;
import com.example.user.infrastructure.mapper.UserStatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;
import static com.example.shared.constant.ForumConstant.TOPIC_COMMENT;
import static com.example.shared.constant.ForumConstant.TOPIC_FOLLOW;
import static com.example.shared.constant.ForumConstant.TOPIC_LIKE;
import static com.example.shared.constant.ForumConstant.TOPIC_PUBLISH;

/**
 * 演示数据 seeder：仅在 {@code spring.profiles.active=seed} 启动时执行一次。
 * <p><b>幂等：</b>启动前若 {@code alice} 用户已存在，整体跳过，多次启动不会重复灌库。
 * <p><b>设计原则：</b>
 * <ul>
 *   <li><b>用户：</b>直接 mapper 写入（绕过 {@code UserService.register} 的邮件 + 邮箱唯一性校验 + 默认未激活），
 *       手动 BCrypt + 同步建 {@code user_statistics} 行（FK 强制 + listener 增量更新前提）。</li>
 *   <li><b>帖子 / 评论 / 点赞 / 关注 / 私信：</b>走 application service 接口，并模拟 controller 发 Kafka 事件，
 *       保证 MySQL / Redis / ES / 读模型 / 通知五方一致。</li>
 *   <li><b>conversationId：</b>{@code smallerId_biggerId}，与 {@code MessageController.buildConversationId} 对齐。</li>
 *   <li><b>SYSTEM 用户排第一：</b>MySQL auto-increment 第一行 id=1，正好等于 {@code ForumConstant.SYSTEM_USER_ID}。</li>
 * </ul>
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserMapper userMapper;
    private final UserStatisticsMapper userStatisticsMapper;
    private final PasswordEncoder passwordEncoder;

    private final DiscussPostService discussPostService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final FollowService followService;
    private final MessageService messageService;
    private final EventProducer eventProducer;

    private final Map<String, Integer> userIdByRef = new HashMap<>();
    private final Map<String, Integer> postIdByRef = new HashMap<>();
    private final Map<String, Integer> commentIdByRef = new HashMap<>();

    @Override
    public void run(String... args) {
        if (userMapper.existsUsername("alice")) {
            log.info("DataSeeder: demo data already present, skipping");
            return;
        }
        log.info("DataSeeder: start");
        seedUsers();
        seedPosts();
        seedComments();
        seedLikes();
        seedFollows();
        seedMessages();
        log.info("DataSeeder: done — users={}, posts={}, comments={}, likes={}, follows={}, messages={}",
                userIdByRef.size(), postIdByRef.size(), commentIdByRef.size(),
                SeedData.LIKES.size(), SeedData.FOLLOWS.size(), SeedData.MESSAGES.size());
    }

    private void seedUsers() {
        String passwordHash = passwordEncoder.encode(SeedData.DEMO_PASSWORD);
        for (SeedData.SeedUser u : SeedData.USERS) {
            User user = User.builder()
                    .username(u.username())
                    .password(passwordHash)
                    .email(u.email())
                    .type(u.type())
                    .status(User.STATUS_ACTIVATED)
                    .activationCode("")
                    .avatarUrl(u.avatarUrl())
                    .createTime(new Date())
                    .build();
            userMapper.insert(user);
            userStatisticsMapper.insert(UserStatistics.builder().userId(user.getId()).build());
            userIdByRef.put(u.ref(), user.getId());
        }
    }

    private void seedPosts() {
        for (SeedData.SeedPost p : SeedData.POSTS) {
            int authorId = requireUserId(p.authorRef());
            DiscussPost post = DiscussPost.builder()
                    .userId(authorId)
                    .title(p.title())
                    .content(p.content())
                    .type(p.type())
                    .status(p.status())
                    .createTime(new Date())
                    .build();
            discussPostService.insertDiscussPost(post);

            // PostController.create: 发 ES 索引事件 + 入 score refresh 队列
            eventProducer.fireEvent(new Event()
                    .setTopic(TOPIC_PUBLISH)
                    .setUserId(authorId)
                    .setEntityType(ENTITY_TYPE_POST)
                    .setEntityId(post.getId()));
            discussPostService.markForScoreRefresh(post.getId());

            postIdByRef.put(p.ref(), post.getId());
        }
    }

    private void seedComments() {
        for (SeedData.SeedComment c : SeedData.COMMENTS) {
            int authorId = requireUserId(c.authorRef());
            int targetOwnerId = requireUserId(c.targetAuthorRef());
            int entityId = resolveEntityId(c.entityType(), c.entityRef());
            int postId = c.entityType() == ENTITY_TYPE_POST
                    ? entityId
                    : resolvePostIdOfComment(c.entityRef());

            Comment comment = Comment.builder()
                    .userId(authorId)
                    .entityType(c.entityType())
                    .entityId(entityId)
                    .targetId(targetOwnerId)
                    .content(c.content())
                    .status(0)
                    .createTime(new Date())
                    .build();
            commentService.addComment(comment);

            // CommentController.add: 发评论通知；若是一级评论再发 PUBLISH 刷帖子热度
            eventProducer.fireEvent(new Event()
                    .setTopic(TOPIC_COMMENT)
                    .setUserId(authorId)
                    .setEntityType(c.entityType())
                    .setEntityId(entityId)
                    .setEntityUserId(targetOwnerId)
                    .setData("postId", postId));
            if (c.entityType() == ENTITY_TYPE_POST) {
                eventProducer.fireEvent(new Event()
                        .setTopic(TOPIC_PUBLISH)
                        .setUserId(authorId)
                        .setEntityType(ENTITY_TYPE_POST)
                        .setEntityId(entityId));
                discussPostService.markForScoreRefresh(entityId);
            }

            commentIdByRef.put(c.ref(), comment.getId());
        }
    }

    private void seedLikes() {
        for (SeedData.SeedLike l : SeedData.LIKES) {
            int userId = requireUserId(l.userRef());
            int entityUserId = requireUserId(l.entityAuthorRef());
            int entityId = resolveEntityId(l.entityType(), l.entityRef());

            int likeStatus = likeService.like(userId, l.entityType(), entityId, entityUserId);
            // LikeController.like: 仅新增点赞时发通知
            if (likeStatus == 1) {
                eventProducer.fireEvent(new Event()
                        .setTopic(TOPIC_LIKE)
                        .setUserId(userId)
                        .setEntityType(l.entityType())
                        .setEntityId(entityId)
                        .setEntityUserId(entityUserId)
                        .setData("postId", resolvePostIdForLike(l)));
            }
        }
    }

    private void seedFollows() {
        for (SeedData.SeedFollow f : SeedData.FOLLOWS) {
            int userId = requireUserId(f.userRef());
            int entityUserId = requireUserId(f.entityAuthorRef());
            int entityId = resolveEntityId(f.entityType(), f.entityRef());

            followService.follow(userId, f.entityType(), entityId, entityUserId);
            // FollowController.follow: 发关注通知（follow service 已做幂等，重复点不会重复发本地事件，
            // 但 Kafka 通知由 controller 决定 —— seeder 首次 seed 是干净的）
            eventProducer.fireEvent(new Event()
                    .setTopic(TOPIC_FOLLOW)
                    .setUserId(userId)
                    .setEntityType(f.entityType())
                    .setEntityId(entityId)
                    .setEntityUserId(entityUserId));
        }
    }

    private void seedMessages() {
        for (SeedData.SeedMessage m : SeedData.MESSAGES) {
            int fromId = requireUserId(m.fromRef());
            int toId = requireUserId(m.toRef());
            Message msg = Message.builder()
                    .fromId(fromId)
                    .toId(toId)
                    .conversationId(buildConversationId(fromId, toId))
                    .content(m.content())
                    .status(0)
                    .createTime(new Date())
                    .build();
            messageService.addMessage(msg);
        }
    }

    // ----- helpers -----

    private int requireUserId(String ref) {
        Integer id = userIdByRef.get(ref);
        if (id == null) throw new IllegalStateException("Unknown user ref: " + ref);
        return id;
    }

    private int requirePostId(String ref) {
        Integer id = postIdByRef.get(ref);
        if (id == null) throw new IllegalStateException("Unknown post ref: " + ref);
        return id;
    }

    private int requireCommentId(String ref) {
        Integer id = commentIdByRef.get(ref);
        if (id == null) throw new IllegalStateException("Unknown comment ref: " + ref);
        return id;
    }

    private int resolveEntityId(int entityType, String ref) {
        return switch (entityType) {
            case ENTITY_TYPE_POST -> requirePostId(ref);
            case ENTITY_TYPE_COMMENT -> requireCommentId(ref);
            case ENTITY_TYPE_USER -> requireUserId(ref);
            default -> throw new IllegalStateException("Unsupported entity type: " + entityType);
        };
    }

    /** 给回复评论场景用：递归查 seed 数据中该评论挂在哪个 post 下。 */
    private int resolvePostIdOfComment(String commentRef) {
        for (SeedData.SeedComment c : SeedData.COMMENTS) {
            if (!c.ref().equals(commentRef)) continue;
            return c.entityType() == ENTITY_TYPE_POST
                    ? requirePostId(c.entityRef())
                    : resolvePostIdOfComment(c.entityRef());
        }
        throw new IllegalStateException("Unknown comment ref: " + commentRef);
    }

    /** 点赞通知里的 postId：赞帖子直接给帖子 id；赞评论给评论所属帖子 id；其他 0。 */
    private int resolvePostIdForLike(SeedData.SeedLike like) {
        return switch (like.entityType()) {
            case ENTITY_TYPE_POST -> requirePostId(like.entityRef());
            case ENTITY_TYPE_COMMENT -> resolvePostIdOfComment(like.entityRef());
            default -> 0;
        };
    }

    private static String buildConversationId(int a, int b) {
        return a < b ? (a + "_" + b) : (b + "_" + a);
    }
}
