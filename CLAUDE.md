# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot forum web app (nowCoder Forum) refactored into a multi-module REST API backend. Frontend is a separate Vue3 SPA repo (`nowcoder-forum-web`); infrastructure (MySQL/Redis/Kafka KRaft/ES + Nginx) lives in `nowcoder-forum-infra`. Supports user registration, posting, commenting, liking, following, private messaging, Elasticsearch-based search, and admin data analytics.

## Tech Stack

- **Framework**: Spring Boot 3.4.1, Java 21 (virtual threads enabled), Maven multi-module
- **Web**: Spring MVC, REST only (Thymeleaf removed in P6)
- **Database**: MySQL 8 + MyBatis Plus; schema migrations via **Flyway** (`system/src/main/resources/db/migration/`)
- **Search**: Elasticsearch (`SearchablePostRepository` extends `ElasticsearchRepository`)
- **Cache / Stats**: Redis (likes, follows, login tickets, user cache, UV/DAU with HyperLogLog and bitmaps)
- **Messaging**: Kafka for cross-module async events (notification, search index); Spring `ApplicationEventPublisher` for in-process domain events (read-model maintenance)
- **Scheduling**: Quartz (`job-store-type: memory`) for post score refresh
- **Security**: Spring Security 6 + opaque ticket auth (Bearer header, validated via Redis)
- **Build**: `./mvnw` (Maven wrapper)

## Common Commands

```bash
# Install all modules to local repo (required before root can resolve them)
./mvnw clean install -DskipTests

# Run the application from the system module (REST API on port 8080, no context-path)
./mvnw -pl system spring-boot:run

# Run all tests
./mvnw test

# Run a single test class / method
./mvnw -pl system test -Dtest=NowCoderApplicationTests
./mvnw -pl system test -Dtest=NowCoderApplicationTests#contextLoads

# Build
./mvnw clean package
```

## Architecture

### Multi-Module Structure

根 POM (`com.example:nowcoder-parent`) 是聚合父 POM，继承 `spring-boot-starter-parent:3.4.1`，管理 7 个子模块。系统入口在 `system/.../NowCoderApplication.java`（`scanBasePackages = "com.example"`）。

```
nowcoder-parent (pom)
├── shared/          ← 跨模块共享（exception、constant、result、util、event、messaging）
├── user/            ← 用户模块（注册、登录、个人信息、user_statistics 读模型）
├── post/            ← 帖子模块（discuss_post + 本地 like_count 读模型）
├── interaction/     ← 互动模块（评论、点赞、关注 — 行为发起方，发布领域事件）
├── message/         ← 消息模块（私信、系统通知）
├── search/          ← 搜索模块（Elasticsearch）
└── system/          ← 系统模块（应用入口、数据统计、定时任务、验证码、Flyway 迁移）
```

模块间依赖（pom 实际）：

| 模块 | 依赖 |
|---|---|
| `shared` | （无内部依赖） |
| `user` | `shared` |
| `post` | `shared`, `user` |
| `interaction` | `shared`, `user`, `post` |
| `message` | `shared`, `user` |
| `search` | `shared`, `user`, `post`, `interaction` |
| `system` | `shared`, `user`, `post`, `interaction`, `search`, `message` |

**业务循环已解耦**：之前 `post → interaction`（查 likeCount）、`user → interaction`（查 followerCount）的反向依赖已通过 **领域事件 + 本地读模型** 拆掉。post 直接读自己的 `discuss_post.like_count`，user 直接读自己的 `user_statistics`，详见 § Domain Events。`interaction` 仍然依赖 `user`/`post` 是合理的（评论/点赞天然依附主体）。

### Request Flow & Authentication

1. `AuthTokenFilter`（在 `UsernamePasswordAuthenticationFilter` 之前）读取 `Authorization: Bearer <ticket>`，从 Redis 校验 `LoginTicket`，把 `User` 作为 principal 填入 `SecurityContextHolder`。
2. Controller 通过 `@AuthenticationPrincipal User me` 拿当前用户，无需手写 `SecurityContextHolder` 调用（已删 `SecurityUtil`）。
3. `SecurityConfig.authorizeHttpRequests` 按 URL 维度做权限：
   - `user`/`admin`/`moderator`：写操作（post/comment/like/follow/letter/avatar/password）
   - `moderator` only：`/posts/*/top`、`/posts/*/wonderful`
   - `admin` only：`/posts/*` DELETE、`/admin/**`
4. `GlobalExceptionHandler`（在 `shared`）统一 catch `BizException` 子类（`AuthException`/`ResourceNotFoundException`/`ValidationException`），返回 JSON `Result`。

### Data Access Patterns

- **MyBatis Plus**: Mappers in each module's `infrastructure/mapper/`, XML in `*/src/main/resources/mapper/` (实际只有 `message` 模块用 XML，其他全走 lambda wrapper)。Pagination uses MP `Page`.
- **Elasticsearch**: `SearchablePostRepository` (in `search/.../infrastructure/mapper/`) for CRUD; `SearchablePostRepositoryImpl` (Spring Data fragment) handles custom highlight search 并返回 `SearchResult` Read Model（与 `DiscussPost` 解耦）。
- **Redis**: Key conventions centralized in `RedisKeyUtil`.

### Caching Strategy & Read Models

- **Users**: `UserServiceImpl` caches users in Redis (`user:{id}`, TTL 1h); cache cleared on updates.
- **Login tickets**: Stored only in Redis (`ticket:{ticket}`); MySQL table dropped。`LoginTicket` 类在 `user/application/dto/`（不是 domain，纯缓存 DTO）。
- **Likes (raw set)**: Who-liked-what is a Redis Set (`like:entity:{type}:{id}`); per-user total like count is a Redis counter (`like:user:{userId}`).
- **Likes (aggregate counts)**: Persisted on the entity:
  - `discuss_post.like_count` — maintained by `PostLikeEventListener`
  - `comment.like_count` — maintained by `CommentLikeEventListener`
  - `user_statistics.received_like_count` — maintained by `UserStatsEventListener`
- **Follows (raw graph)**: Sorted sets with timestamps as scores (`followee:{userId}:{entityType}`, `follower:{entityType}:{entityId}`) — used for paginated follower/followee lists ordered by follow time.
- **Follows (aggregate counts)**: `user_statistics.follower_count` / `followee_count` — maintained by `UserStatsEventListener`.
- **`likeStatus` (per-user, per-entity)**: NOT aggregated; queried at read time via `LikeService.findEntityLikeStatuses(userId, entityType, entityIds)` (Redis SISMEMBER pipelined). The view layer assembles aggregate count + per-user status before returning.

### Domain Events (Spring local) — Read-Model Maintenance

`interaction` 是写侧。每次 `like` / `unlike` / `follow` / `unfollow` 后发一个 Spring `ApplicationEvent`（进程内，不走 Kafka）：

- `EntityLikedEvent` / `EntityUnlikedEvent` (`shared/.../event/`)
- `FollowEvent` / `UnfollowEvent`

读侧 listener 各自维护本地表：

| Listener | Module | Table | Trigger |
|---|---|---|---|
| `PostLikeEventListener`    | post | `discuss_post.like_count`         | EntityType=POST |
| `CommentLikeEventListener` | interaction | `comment.like_count`       | EntityType=COMMENT |
| `UserStatsEventListener`   | user | `user_statistics.{received_like_count, follower_count, followee_count}` | per event type |

`user_statistics` 行由 `UserServiceImpl.register()` 在同一事务里 INSERT —— 保证后续 `UPDATE … WHERE user_id = ?` 一定命中已有行，listener 可以保持纯增量（不需要 UPSERT）。

### Event-Driven Messaging (Kafka — cross-process notifications)

- `EventProducer` (in `shared`) publishes JSON events to Kafka topics for **cross-module side effects** (notifications, search index).
- **Message** module: `NotificationEventConsumer` listens `comment`/`like`/`follow` → creates system notification messages.
- **Search** module: `SearchIndexEventConsumer` listens `publish`/`delete` → indexes/removes posts in Elasticsearch.
- Topics are constants in `ForumConstant`.

> **何时用哪种？** Kafka 给"另一个 bounded context 的副作用"（通知、搜索索引）；Spring 本地事件给"同进程内的读模型"（点赞数、粉丝数）。后者无需 broker、零延迟、随事务一起回滚。

### Scheduled Jobs (Quartz)

- `PostScoreRefreshJob` (in `system` module) runs every 5 minutes. It pops post IDs from a Redis set (`post:score`), recalculates score via `DiscussPost.calculateScore(...)`，updates the DB, and re-indexes the post in Elasticsearch.

### Configuration Notes

- **Root config**: `system/src/main/resources/application.yaml` 是唯一真实配置源（datasource、redis、kafka、es、mail、flyway、mybatis-plus）。
- `application.yaml` imports `.env` via `optional:file:.env[.properties]`.
- Required env vars: `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `UPLOAD_PATH`. Optional: `CAPTCHA_PROVIDER` (default `kaptcha`), Tencent captcha keys.
- **No context path**: backend serves at `http://localhost:8080/api/v1/...`
- **Flyway**: enabled in `system/.../application.yaml`. Migrations live at `system/src/main/resources/db/migration/`. The `forum` database itself must be created externally before first boot (`CREATE DATABASE forum DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`); Flyway then handles all tables. `baseline-on-migrate: true` 保证已有库首次接 Flyway 不会失败。
- **MyBatis Plus `type-aliases-package`** (root `application.yaml`): `com.example.user.domain,com.example.post.domain.entity,com.example.interaction.domain.entity,com.example.message.domain.entity`. 新增模块时需同步更新。
- The `NowCoderApplication` `@PostConstruct` sets `es.set.netty.runtime.available.processors=false` to avoid a Netty/Elasticsearch startup conflict.

### Transactional Policy

| 场景 | 是否加 `@Transactional` | 说明 |
|---|---|---|
| **单条单表写入**（update by id / insert one row） | ❌ 不加 | Spring Data 默认 auto-commit 已足够；加事务反而增加开销 |
| **同方法内多表写入**（user + user_statistics） | ✅ 必须加 | 保证原子性，任一失败全回滚 |
| **同方法内"写+读再写"**（comment insert + post comment_count update） | ✅ 必须加 | 并发下非事务可能导致计数不一致；comment 用 `READ_COMMITTED` 降低锁竞争 |
| **纯 Redis 操作**（like / follow 的 ZSet/Set 操作） | ❌ 不加 | Redis 无 ACID 事务参与；通过 Lua 脚本保证原子性 |
| **Redis 操作 + 发本地事件**（`ApplicationEventPublisher`） | ❌ 不加 | 事件失败不会回滚 Redis，这是已知设计选择（最终一致性） |
| **纯查询** | ❌ 不加 | 无写操作，事务无意义 |

**当前规约对照：**
- `UserServiceImpl.register` — `@Transactional`（user + user_statistics 双表写入）✅
- `CommentServiceImpl.addComment` — `@Transactional(READ_COMMITTED)`（comment insert + discuss_post.comment_count update）✅
- `UserServiceImpl.activation` / `updatePassword` — 无注解（单条 update）✅
- `DiscussPostServiceImpl.insertDiscussPost` — 无注解（单写）✅
- `MessageServiceImpl.addMessage` — 无注解（单写）✅
- `LikeServiceImpl.like` / `FollowServiceImpl.follow` — 无注解（Redis + 事件）✅

## Module Package Layout

每个模块内部按 DDD 四层组织：

```
com.example.<module>/
├── interfaces/          ← REST Controller、DTO、VO、异常处理器、注解
│   ├── rest/
│   ├── dto/
│   ├── vo/
│   └── handler/
├── application/         ← Application Service（用例编排）
│   ├── service/
│   ├── service/impl/
│   └── dto/             ← 跨层 DTO（如 LoginTicket、PostItem）
├── domain/              ← 领域实体（Entity）+ 领域服务
│   └── entity/
└── infrastructure/      ← Mapper、Repository、Config、Security、MQ、Cache、Util
    ├── mapper/          ← MyBatis Mapper（注：search 的 ES Repository 也在这里，命名待修）
    ├── config/
    ├── repository/
    ├── security/
    ├── event/           ← 领域事件 listener
    ├── messaging/       ← Kafka producer/consumer
    └── util/
```

### 模块间依赖规则

1. **禁止跨模块访问 infrastructure**（如 user 模块直接调 post 模块的 Mapper）
2. 模块间通信通过 **application Service 接口** 或 **领域事件（Domain Event）**
3. 共享代码（`BizException`、`ForumConstant`、`Result`、`RedisKeyUtil`、`AuthorRef`）收敛到 `shared` 包
4. 跨模块 DTO 不持有他人 domain 实体，统一用 `shared.dto.AuthorRef`（防腐层 ValueObject）
5. 每个模块可独立演进，未来拆微服务时整体平移即可

---

## Changelog

> 已完成的重构按时间排序。需要看具体改了什么文件，查 `git log` 对应日期。

| 阶段 | 完成时间 | 概要 |
|---|---|---|
| **P0–P5** | ~2026-04 之前 | 模块拆分 + REST API + Vue3 前端迁移 |
| **P6** | 2026-04-21 | 删 Thymeleaf / 老 Controller / `org.example.nowcoder.*` monolith；Kafka KRaft；去 `/forum` context-path |
| **P7** | 2026-05-06 | 业务循环解耦：3 个本地读模型（`discuss_post.like_count` / `comment.like_count` / `user_statistics`）+ 4 个 Spring 领域事件；`UserServiceImpl.register` 同事务建 stats 行；接入 Flyway |
| **R1** | 2026-05-07 | DDD 硬伤修复：`User` → POJO + `UserDetailsAdapter`；`DiscussPost` 拆出 `SearchablePost`；去掉三个 Service 的 `extends ServiceImpl`；`FollowController` 不再注 `UserStatisticsMapper`；Controller 不再直接操作 `RedisTemplate` |
| **R2** | 2026-05-07 | Domain 充血：实体加领域行为（`activate` / `markAsTop` / `softDelete` / `isOnPost` 等）；抽 `ContentSanitizer`；算分公式收进 `DiscussPost.calculateScore`；`AuthorRef` ValueObject 替换跨模块 DTO 中的 `User` |
| **R3** | 2026-05-07 | 包结构 & Read Model：`SearchResult` 独立高亮字段；user/system 包路径统一；删除 `SecurityUtil` 改用 `@AuthenticationPrincipal`；`LoginTicket` 移到 `application/dto/`；`PostScoreRefreshJob` 从 record 改 class |
| **N1** | 2026-05-08 | 移除 PageHelper（pom + yaml + 死代码）；Transactional Policy 规约写入本文件 |
| **Audit** | 2026-05-09 | Post-R3 全量审计，发现 23 项新问题 → 见下方 TODO |
| **P0** | 2026-05-09 | Bug 修复：`AuthorRef.deleted()` 占位修作者删除 NPE（DiscussPost / Message 用占位、Follow 用 filter）；`SimpleDateFormat` → `DateTimeFormatter`；`MessageController` 的 `Integer.parseInt` 包成 `ResourceNotFoundException` |
| **P1** | 2026-05-09 | 配置 + 死代码清理：删 6 个 module-level `application.yml`（唯一源 = `system/application.yaml`）；删根 `src/test/java/org/example/nowcoder/`（P6 残留）；`type-aliases-package` user 项对齐 `domain.entity`；5 处死代码（loginTicketMapper 注释 / DiscussPostMapper 注释方法 / PostScoreRefreshJob 注释 / `selectByEmail` / `IService` import） |

---

## TODO（按优先级）

> 来源：2026-05-09 审计 + R1~R3 没收的尾巴 + 长期技术债。每完成一项就把 `[ ]` 改成 `[x]` 并标完成日期。
>
> P0（bug）+ P1（配置/死代码）已于 2026-05-09 完成，移到 changelog。编号保留以便和 commit message 对照。

### P2 — DDD 收尾（R1~R3 没扫干净的 corner）

- [ ] **P2.1** `user/.../AuthController.java:23,52,58,92` 还在直接注 `RedisTemplate` 存 captcha — 同 R1.5 模式抽到 `KaptchaService.cacheCaptcha(owner, text)`
- [ ] **P2.2** `search/.../infrastructure/mapper/SearchablePostRepository.java` 是 ES Repository 不是 MyBatis Mapper — 重命名目录到 `infrastructure/repository/` 并改 `NowCoderApplication` 的 `@EnableElasticsearchRepositories` 包路径
- [ ] **P2.3** 3 处把 `findDiscussPostById(id, userId).discussPost()` 当存在性检查用（顺带一次无谓的 user 表查询）→ 改用 `discussPostService.getRawPost(id)`：
  - `interaction/.../CommentController.java:82` `resolveTargetOwner`
  - `interaction/.../PostCommentController.java:72` `requirePostExists`
  - `search/.../SearchIndexEventConsumer.java:40` `handlePublish`
- [ ] **P2.4** `DiscussPostService.findDiscussPostById(int id, int userId)` 中 `userId` 参数 impl 始终传 0（不查 likeStatus）→ 要么真实现 per-user likeStatus，要么从签名删掉
- [ ] **P2.5** `user/.../UserDetailsAdapter.java:64` `return user.getStatus() == 1` 用魔数 → 改 `return user.isActivated()`
- [ ] **P2.6** 实体风格统一：`message.../Message` 和 `user.../UserStatistics` 还是 `@Data`（mutable + 全 setter），其他实体已经是 `@Getter` + 域方法 → 二选一
- [ ] **P2.7** Logger 声明统一：`UserController.java:45` / `AuthController.java:48` 的 `final Logger log = LoggerFactory.getLogger(getClass())` 改类级 `@Slf4j`
- [ ] **P2.8** `search/.../SearchController.java:41` `throws Exception` 删掉（方法体不抛 checked）
- [ ] **P2.9** `message/.../MessageServiceImpl.java:163` `addMessage` 直接 mutate 入参 → 改 builder 重建实例（与 `DiscussPostServiceImpl` 风格一致）
- [ ] **P2.10** `user/.../AuthController.java:125` `SecurityContextHolder.clearContext()` 在 stateless app 是 no-op → 删掉

### P3 — 性能（并发下能感知）

- [ ] **P3.1** `interaction/.../PostCommentController.java:80` `buildCommentVo` 三连击：
  - 每个父评论触发独立的回复查询（N+1 — 10 个一级评论 = 10+ 次额外 DB roundtrip）
  - `pageSize=Integer.MAX_VALUE` 在大评论流下直接 OOM
  - `userService.listByIds(authorReplyIds)` 与 `listByIds(targetReplyIds)` 是两次独立调用，应取并集后单次查
- [ ] **P3.2** `message/.../MessageServiceImpl.findDms` / `findNotices` 分别 `listByIds(fromIds)` 和 `listByIds(toIds)` → 合并成 `listByIds(union(from, to))`

### P4 — 架构（工作量大，简历项目可暂缓）

- [ ] **P4.1** `shared` 模块拆分 → `web-starter` / `messaging-starter` / `security-starter` / `captcha` 独立模块（当前 shared 是"跨模块复用代码垃圾桶"）
- [ ] **P4.2** Kafka `Event` 类型安全化：按 topic 拆成 `PublishPostEvent` / `LikeKafkaEvent` / `FollowKafkaEvent` 等 record（当前是 `Map<String,Object>` 散装事件）
- [ ] **P4.3** Quartz `memory` → `jdbc`：建 `qrtz_*` 表，配合 infra 仓库部署
- [ ] **P4.4** `system` 模块解耦：Quartz job 目前调用了几乎所有业务 service，拆微服务时需改为异步事件
- [ ] **P4.5** 拆微服务时 `search → user`（搜索结果展示作者信息）的运行时调用要改 RPC/HTTP

### P5 — 测试 & 简历准备（非代码）

- [ ] **P5.1** 补充领域方法单元测试（`User.activate()` / `DiscussPost.calculateScore()` / `Comment.isOnPost()` 等）
- [ ] **P5.2** 更新简历：把 DDD 改造（贫血→充血、去框架污染、领域事件解耦、Read Model）写进项目亮点
- [ ] **P5.3** 准备面试话术：每个改造点的 Why（为什么拆 UserDetails？为什么不用 `extends ServiceImpl`？Read Model 解决什么问题？）
- [ ] **P5.4** 技术博客：《从 Transaction Script 到 Rich Domain Model 的实践》或类似主题

---

### 推荐执行顺序

1. **P2.1 ~ P2.4** — DDD 关键收尾（其他 P2.x 是风格问题，可放后面）
2. **P3** — 真上量了再做
3. **P4** — 简历项目可不做，但要能讲清楚
4. **P5** — 阶段性收口