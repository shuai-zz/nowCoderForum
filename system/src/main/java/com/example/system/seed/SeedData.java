package com.example.system.seed;

import java.util.List;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_COMMENT;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;
import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_USER;
import static com.example.post.domain.entity.DiscussPost.STATUS_NORMAL;
import static com.example.post.domain.entity.DiscussPost.STATUS_WONDERFUL;
import static com.example.post.domain.entity.DiscussPost.TYPE_NORMAL;
import static com.example.post.domain.entity.DiscussPost.TYPE_TOP;
import static com.example.user.domain.entity.User.TYPE_ADMIN;
import static com.example.user.domain.entity.User.TYPE_MODERATOR;
import static com.example.user.domain.entity.User.TYPE_USER;

/**
 * 演示数据集合。
 * <p>所有跨实体关联用字符串 ref 描述（{@code "alice"} / {@code "p_hello_world"} / {@code "c_hw_1"}），
 * 由 {@link DataSeeder} 在运行时解析成真实的 DB auto-increment id。
 * <p>这一层不依赖任何 service / mapper，纯数据，方便整理 / review。
 */
final class SeedData {

    private SeedData() {}

    /** demo 账号统一密码（BCrypt 加密后写入 user.password）。 */
    static final String DEMO_PASSWORD = "password123";

    record SeedUser(String ref, String username, String email, int type, String avatarUrl) {}
    record SeedPost(String ref, String authorRef, String title, String content, int type, int status) {}
    record SeedComment(String ref, String authorRef, int entityType, String entityRef,
                       String targetAuthorRef, String content) {}
    record SeedLike(String userRef, int entityType, String entityRef, String entityAuthorRef) {}
    record SeedFollow(String userRef, int entityType, String entityRef, String entityAuthorRef) {}
    record SeedMessage(String fromRef, String toRef, String content) {}

    /**
     * SYSTEM 必须排第一 —— MySQL auto-increment 从 1 开始，第一个 INSERT 的 user.id 一定是 1，
     * 对应 {@code ForumConstant.SYSTEM_USER_ID}。NotificationEventConsumer 写通知时把 fromId 填成 1。
     */
    static final List<SeedUser> USERS = List.of(
            new SeedUser("system",       "SYSTEM",   "system@nowcoder.com",   TYPE_ADMIN,     "https://images.nowcoder.com/head/notify.png"),
            new SeedUser("admin",        "admin",    "admin@nowcoder.com",    TYPE_ADMIN,     "https://images.nowcoder.com/head/1t.png"),
            new SeedUser("mod_guanyu",   "guanyu",   "guanyu@nowcoder.com",   TYPE_MODERATOR, "https://images.nowcoder.com/head/102t.png"),
            new SeedUser("mod_zhangfei", "zhangfei", "zhangfei@nowcoder.com", TYPE_MODERATOR, "https://images.nowcoder.com/head/103t.png"),
            new SeedUser("alice",        "alice",    "alice@nowcoder.com",    TYPE_USER,      "https://images.nowcoder.com/head/111t.png"),
            new SeedUser("bob",          "bob",      "bob@nowcoder.com",      TYPE_USER,      "https://images.nowcoder.com/head/112t.png"),
            new SeedUser("charlie",      "charlie",  "charlie@nowcoder.com",  TYPE_USER,      "https://images.nowcoder.com/head/113t.png"),
            new SeedUser("david",        "david",    "david@nowcoder.com",    TYPE_USER,      "https://images.nowcoder.com/head/138t.png"),
            new SeedUser("emma",         "emma",     "emma@nowcoder.com",     TYPE_USER,      "https://images.nowcoder.com/head/145t.png"),
            new SeedUser("frank",        "frank",    "frank@nowcoder.com",    TYPE_USER,      "https://images.nowcoder.com/head/149t.png")
    );

    static final List<SeedPost> POSTS = List.of(
            new SeedPost("p_warm_spring", "admin", "互联网求职暖春计划",
                    "今年的就业形势，确实不容乐观。过了个年，仿佛跳水一般，整个讨论区哀鸿遍野！" +
                            "为了帮助大家度过寒冬，牛客网特别联合 60+ 家企业，开启互联网求职暖春计划，面向 18 届 & 19 届，拯救 0 offer！",
                    TYPE_TOP, STATUS_NORMAL),
            new SeedPost("p_hello_world", "alice", "Hello",
                    "Hello World! 这是我用 Spring Boot 写的第一个帖子。", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_xuanxue", "alice", "玄学帖",
                    "据说玄学贴很灵验，求大佬捞捞我这个菜鸡给个机会！", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_fashion_tech", "alice", "揭秘时尚科技的力量",
                    "它是最时尚的互联网公司之一，致力于帮助人们发现流行趋势；它是专注于科技的电商企业，力图让人们享受更优质的购物体验。",
                    TYPE_NORMAL, STATUS_WONDERFUL),
            new SeedPost("p_es_spring", "alice", "Spring Boot 整合 Elasticsearch",
                    "Elasticsearch 是一款分布式搜索引擎框架，本文记录 Spring Boot 集成 ES 的踩坑过程。", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_good_morning", "charlie", "Good", "Good Morning!", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_public_main", "david", "public",
                    "public static void main(String[] args)", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_haha", "emma", "哈哈", "哈哈哈哈，今天好开心！", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_want_offer", "bob", "我要 offer",
                    "跪求 offer~~~ 大佬们指点一下面经吧！", TYPE_TOP, STATUS_NORMAL),
            new SeedPost("p_admin_notice", "admin", "管理员公告",
                    "请大家文明发言，不要灌水。违规帖子会被删除。", TYPE_TOP, STATUS_WONDERFUL),
            new SeedPost("p_newbie", "frank", "新人报道", "新人报道，请多关照！", TYPE_NORMAL, STATUS_NORMAL),
            new SeedPost("p_spring_cache", "frank", "Spring Cache",
                    "Spring Cache 配合 RedisCacheManager 的使用心得，适合八股复习。", TYPE_NORMAL, STATUS_NORMAL)
    );

    /**
     * 注意：回复评论时 {@code targetAuthorRef} 必须是被回复评论 ({@code entityRef}) 的作者，
     * 而不是被回复评论的 target —— 与 CommentController.add 中 resolveTargetOwner 行为一致。
     */
    static final List<SeedComment> COMMENTS = List.of(
            // p_hello_world
            new SeedComment("c_hw_1", "bob",     ENTITY_TYPE_POST,    "p_hello_world", "alice", "Spring Boot 牛逼！"),
            new SeedComment("c_hw_2", "charlie", ENTITY_TYPE_POST,    "p_hello_world", "alice", "Kafka 也不错"),
            new SeedComment("c_hw_3", "david",   ENTITY_TYPE_POST,    "p_hello_world", "alice", "说道心坎里去了!!!"),

            // p_xuanxue（带二级回复）
            new SeedComment("c_xx_1",  "bob",     ENTITY_TYPE_POST,    "p_xuanxue", "alice", "哈哈哈玄学有用!"),
            new SeedComment("c_xx_2",  "charlie", ENTITY_TYPE_POST,    "p_xuanxue", "alice", "前来沾沾喜气"),
            new SeedComment("c_xx_r1", "alice",   ENTITY_TYPE_COMMENT, "c_xx_1",    "bob",   "谢谢老铁!"),

            // p_want_offer（带二级回复）
            new SeedComment("c_wo_1",  "bob",   ENTITY_TYPE_POST,    "p_want_offer", "bob",   "自己顶！"),
            new SeedComment("c_wo_2",  "alice", ENTITY_TYPE_POST,    "p_want_offer", "bob",   "顶你"),
            new SeedComment("c_wo_3",  "frank", ENTITY_TYPE_POST,    "p_want_offer", "bob",   "其实我也想要 offer"),
            new SeedComment("c_wo_r1", "bob",   ENTITY_TYPE_COMMENT, "c_wo_2",       "alice", "谢谢兄弟"),

            // p_newbie（带二级回复）
            new SeedComment("c_nb_1",  "alice", ENTITY_TYPE_POST,    "p_newbie", "frank", "欢迎!"),
            new SeedComment("c_nb_2",  "bob",   ENTITY_TYPE_POST,    "p_newbie", "frank", "欢迎!"),
            new SeedComment("c_nb_r1", "frank", ENTITY_TYPE_COMMENT, "c_nb_1",   "alice", "谢谢"),

            // p_spring_cache
            new SeedComment("c_sc_1", "alice", ENTITY_TYPE_POST, "p_spring_cache", "frank", "干货!"),
            new SeedComment("c_sc_2", "david", ENTITY_TYPE_POST, "p_spring_cache", "frank", "RedisCacheManager 文档不全，看这个学了不少")
    );

    static final List<SeedLike> LIKES = List.of(
            new SeedLike("bob",     ENTITY_TYPE_POST,    "p_hello_world",  "alice"),
            new SeedLike("charlie", ENTITY_TYPE_POST,    "p_hello_world",  "alice"),
            new SeedLike("alice",   ENTITY_TYPE_POST,    "p_xuanxue",      "alice"),
            new SeedLike("bob",     ENTITY_TYPE_POST,    "p_xuanxue",      "alice"),
            new SeedLike("alice",   ENTITY_TYPE_POST,    "p_want_offer",   "bob"),
            new SeedLike("frank",   ENTITY_TYPE_POST,    "p_want_offer",   "bob"),
            new SeedLike("emma",    ENTITY_TYPE_POST,    "p_want_offer",   "bob"),
            new SeedLike("alice",   ENTITY_TYPE_POST,    "p_newbie",       "frank"),
            new SeedLike("david",   ENTITY_TYPE_POST,    "p_spring_cache", "frank"),
            new SeedLike("alice",   ENTITY_TYPE_COMMENT, "c_hw_1",         "bob"),
            new SeedLike("bob",     ENTITY_TYPE_COMMENT, "c_wo_2",         "alice")
    );

    static final List<SeedFollow> FOLLOWS = List.of(
            new SeedFollow("bob",     ENTITY_TYPE_USER, "alice", "alice"),
            new SeedFollow("charlie", ENTITY_TYPE_USER, "alice", "alice"),
            new SeedFollow("david",   ENTITY_TYPE_USER, "alice", "alice"),
            new SeedFollow("alice",   ENTITY_TYPE_USER, "frank", "frank"),
            new SeedFollow("alice",   ENTITY_TYPE_USER, "emma",  "emma"),
            new SeedFollow("frank",   ENTITY_TYPE_USER, "alice", "alice"),
            new SeedFollow("emma",    ENTITY_TYPE_USER, "bob",   "bob")
    );

    static final List<SeedMessage> MESSAGES = List.of(
            new SeedMessage("bob",     "alice", "你好"),
            new SeedMessage("bob",     "alice", "在吗？"),
            new SeedMessage("alice",   "bob",   "在的，怎么了？"),
            new SeedMessage("bob",     "alice", "想问问 Spring Cache 怎么配置"),
            new SeedMessage("charlie", "alice", "Hello!")
    );
}
