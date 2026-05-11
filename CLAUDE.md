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

# 灌入演示数据（首次启动数据库为空时使用）
# DataSeeder 是 @Profile("seed") 的 CommandLineRunner，会创建 10 个 demo 用户 + 12 个帖子 +
# 15 条评论 + 11 个点赞 + 7 条关注 + 5 条私信，走 service 接口 + Kafka 事件，保证 MySQL/Redis/ES 三方一致。
# 幂等：检测到 alice 用户存在则整体跳过。
# 所有 demo 账号密码统一为 password123（已预激活，无需邮件激活）。
./mvnw -pl system spring-boot:run -Dspring-boot.run.profiles=seed
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
| **P2** | 2026-05-09 | DDD 收尾：抽 `KaptchaService` 解耦验证码（AuthController 不再碰 RedisTemplate）；search ES Repository 目录重命名 `mapper → repository`；修复 3 处 `findDiscussPostById` 存在性检查误用为 `getRawPost`；删除 `findDiscussPostById` 无用 `userId` 参数；`UserDetailsAdapter` 魔数改 `isActivated()`；`Message` / `UserStatistics` 实体风格统一为 `@Getter` + builder；Logger 统一 `@Slf4j`；删除多余 `throws Exception`；`addMessage` 不再 mutate 入参；移除无意义的 `SecurityContextHolder.clearContext()` |
| **P3** | 2026-05-10 | 性能：`MessageServiceImpl.findDms` / `findNotices` 双 `listByIds` 合并成单次 union（P3.2）；`PostCommentController.buildCommentVo` 干掉 N+1 子回复查询 —— 加 `comment.reply_count` 物化列（Flyway V2 + 写侧同事务 refresh）+ 窗口函数 `selectTopRepliesGrouped` 一次拉整页 top-K 回复（P3.1）；整页 user 查询 union 到 controller 顶层；新增 `GET /api/v1/comments/{id}/replies` 加载更多分页接口；`REPLY_PREVIEW_LIMIT=3` |
| **Audit-2** | 2026-05-10 | Post-P3 全量审计（DDD / Bug / 面试硬伤 三维度）：发现 2 项 DEALBREAKER（密码 MD5+5字符 salt、1/122 测试覆盖）、12 项 HIGH/MED bug（avatar 路径遍历 + 上传异常、`findDiscussPostById` NPE、detail 不过滤软删、CommentController 校验顺序、follow 非幂等、ES 索引漏过滤软删、消费者无幂等、UV/DAU 临时 key 无 TTL 等）、9 项 DDD/一致性问题（实体 Lombok 风格 split、PostItem 暴露 domain entity、select-then-update 反 pattern、ServiceLogAspect 用 SimpleDateFormat 违反 P0 等）→ S1/S2/S3 |
| **S1** | 2026-05-10 | 安全 & 致命 bug：MD5+5字符 salt → BCrypt（`UserServiceImpl#register/login/updatePassword` + Flyway V3 删 `user.salt` 列 + 顺手修 `updatePassword` 比对 hash 而非原文的 latent bug）；avatar GET 路径遍历双层防御（白名单正则 + `Path.resolve().normalize().startsWith(baseDir)`）；avatar 上传无后缀 → 走 400 ValidationException；IOException 改抛 `UploadFailedException extends BizException` |
| **S2** | 2026-05-10 ~ 2026-05-11 | 正确性 bug 11 项：`findDiscussPostById` 帖子不存在返回 `PostItem.missing` 而非 NPE；`PostController.detail` 调 `requirePostExists` 过滤软删；`CommentController.add` 校验顺序前置 + `EntityExistenceChecker` 形成 controller/service 双层；Follow LUA 改返回 `added`/`removed`，service 仅 `result==1` 时发 FollowEvent；`PostScoreRefreshJob` / `SearchIndexEventConsumer` 跳过软删帖子；`EventProducer` 序列化失败抛 `IllegalStateException`；`Event.eventId`（UUID）+ `EventIdempotencyGuard`（Redis SETNX，24h TTL）实现两个 Kafka consumer 幂等；`UserStatsEventListener` 0 行命中 warn 日志；`DataServiceImpl` UV/DAU 合并 key 加 10min TTL |
| **S3** | 2026-05-11 | DDD & 一致性收尾 9 项：`ServiceLogAspect` `SimpleDateFormat` → `static DateTimeFormatter` + 删 P6 旧注释；`DiscussPost` 补齐 `@NoArgsConstructor`/`@AllArgsConstructor` 与四件套对齐；`PostItem` 拍平为结构性字段 + `from`/`missing`/`isMissing()`，VO 改接收 PostItem，interfaces 层不再消费 domain entity；`DiscussPostMapper.updateCommentCount/updateScore` 单 UPDATE 替代 select-then-update；三实体新增 `applySanitizedContent(...)` domain 方法替代 service 端 builder 重建；`FollowController` 加 `@RequestMapping("/api/v1")` class-level；删 `PostController.detail` 未用 `me`；`User.canActivateWith` 改 `Objects.equals` 防 NPE |

---

## TODO（按优先级）

> 来源：长期技术债 + 简历 / 面试准备。每完成一项就把 `[ ]` 改成 `[x]` 并标完成日期。
>
> 第一轮 P0~P3、第二轮 S1~S3 已全部完成，详见 changelog。剩余 P4（架构层改造，简历项目可暂缓）+ P5（测试 / 简历 / 博客）。

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

1. **P5.1** — 给读模型 + 事件监听器写测试（半天到 1 天）—— 简历亮点不能没测试
2. **P5.2~5.4** — 简历 / 面试话术 / 博客
3. **P4** — 架构层改造，简历项目可暂缓

---

## Project Highlights / 项目亮点（面试 & 简历素材）

> 把改造过程中值得深挖的技术点汇总在这里，方便写简历和准备面试。

### 1. 实体 immutable 化与 MyBatis 反射 fallback 机制

在统一实体风格时，我们将 `Message` 和 `UserStatistics` 从 Lombok `@Data`（mutable，全 setter）改为 `@Getter` + `@Builder` 的不可变风格。这引发了一个问题：`MessageMapper.xml` 中 `<select resultType="...Message">` 默认通过 **setter** 注入字段值，删掉 setter 后理论上应该绑定失败。

但实际测试发现 `selectById` 仍能正常运行。根本原因是 **MyBatis 的字段绑定 fallback 机制**：

1. MyBatis 优先尝试 `setter` 方法注入；
2. 当 setter 不存在时，自动降级为 **字段反射注入**（`Field.setAccessible(true)` + `field.set()`）；
3. Lombok 的 `@Getter` 不会把字段声明为 `final`，因此反射可以直接写字段。

> **启示**：不可变实体在 MyBatis 下可以工作，但前提是字段非 `final`。若进一步想使用 `final` 字段（真正 immutable），需要显式配置 MyBatis 的构造函数映射（`<constructor>` 或 `@AutomapConstructor`），或引入 MapStruct 做查询层与领域层的完全隔离。

### 2. N+1 消除：窗口函数 + 读模型物化列双管齐下

帖子详情页旧实现：每条一级评论独立查"全部回复 + 两次 user 查询"（reply 作者 / reply target），10 个一级评论 = 30+ 次额外 DB roundtrip；且回复用 `pageSize=Integer.MAX_VALUE` 全量加载，热门帖子下有 OOM 风险。

改造分两层：

1. **物化 `reply_count` 列**：与 `discuss_post.comment_count` / `comment.like_count` 对称，`comment` 表加 `reply_count INT NOT NULL DEFAULT 0`（Flyway V2 + 一次性 backfill），`CommentServiceImpl.addComment` 同事务内 `refreshReplyCount(parentId, count)`。读侧直接拿列，不再 COUNT。
2. **窗口函数批量取 top-K**：MySQL 8 `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY create_time DESC)` 一次性拉整页所有父评论的 top-3 回复，`buildCommentVo` 退化成纯组装。整页所有 user 查询（一级作者 + 回复作者 + 回复 target）union 到 controller 顶层做一次 `listByIds`。

效果：原来 `1 + 1 + N×3` 次查询（N=pageSize），现在固定 2 次 comment + 1 次 user，不随 pageSize 增长。新增 `/api/v1/comments/{id}/replies` 分页接口供前端"展开更多回复"。

> **踩过的坑**：`./mvnw -pl system spring-boot:run` 不带 `-am` 不会重建上游 `interaction` 模块，第一次测试发现窗口函数日志根本没出现 —— 加载的是 `~/.m2/` 里的旧 JAR。跨模块改动后必须 `./mvnw clean install -DskipTests` 或 `-pl system -am spring-boot:run`。

### 3. 密码哈希现代化：从 MD5+5字符 salt 到 BCrypt

旧实现把 `MD5(password + 5字符 salt)` 直接存库。三个独立问题，越往下越致命：

1. **MD5 太快**：现代 GPU 跑 MD5 是 ~100 亿 hash/秒（Hashcat 在 RTX 4090 实测）。8 位字母数字密码（62^8 ≈ 2×10^14）6 小时穷举完。BCrypt strength=10 只有 ~1000 hash/秒，慢 7 个数量级，同一台 GPU 暴破时间从小时级变成宇宙级。
2. **5 字符 hex salt 只有 100 万种可能**：攻击者拿到全表 dump 可以预先对每种 salt 计算 "top 1000 常用密码" 的 MD5，几十 GB SSD 装得下，任何用户用 `123456789` + 任何 salt 秒查。正常 salt 应该是 16 字节随机（2^128 种），让彩虹表预计算永远不可能。
3. **MD5 已破** —— 2004 王小云团队找到第一个碰撞、2008 实用化，密码学社区共识"非密码用途也别再用"。面试官看到 `DigestUtils.md5DigestAsHex` 立刻减分。

替换成 `BCryptPasswordEncoder`（Spring Security 自带），三个核心特性：

| 特性 | 收益 |
|---|---|
| 自适应工作因子 cost=10（2^10 轮 Eksblowfish） | 每年 +1 cost 抵消摩尔定律算力增长 |
| 16 字节随机 salt 内嵌 hash 字符串 | 不需要单独 `salt` 列，schema 简化 |
| 自描述格式 `$2a$10$<22字符salt><31字符hash>` | 可平滑用 `DelegatingPasswordEncoder` 迁移到 Argon2id（2015 年密码哈希竞赛冠军） |

代码改动：3 处 `md5(...) + String.equals` 全部换成 `passwordEncoder.matches(raw, hash)`；Flyway V3 删 `user.salt` 列；`User` 实体去 `salt` 字段；删除 `ForumUtil.md5` 方法。

**意外收获**：旧 `updatePassword` 有个 latent bug —— 校验"新密码不能等于旧密码"用 `newPassword.equals(oldPassword)`，但前面几行 `oldPassword = md5(oldPassword + salt)` 早把 `oldPassword` 变量覆盖成 hash，所以这个等价检查永远是 false。BCrypt 重写改成 `passwordEncoder.matches(newPassword, user.getPassword())` 顺手修了 —— 同一个 hash 永远 match 自己原文。

### 4. 路径遍历漏洞：字符串防御 vs 路径语义防御

旧 avatar GET 接口直接拼 `new File(uploadPath, filename)`，且 SecurityConfig 把这个路径配成 `permitAll`。攻击者：

```
GET /api/v1/users/avatar/..%2F..%2F..%2F.env
```

`%2F` 是 URL 编码的 `/`。Spring `@PathVariable` 自动解码，filename 实际是 `../../../.env`，操作系统层 `File.exists()` 会自动 normalize 路径，最终读到项目根的 `.env`（**含 MySQL 密码 + 邮箱密码**）。`isFile()` 对 `/etc/passwd` 也是 `true`，攻击者能拿任意可读文件。

**为什么常见的字符串防御都不够**：
- `if (filename.contains(".."))` —— `....//`、`%2e%2e%2f`、Unicode normalization 多种绕法
- `filename.replaceAll("..", "")` —— 把合法 `image..png` 也截断，且 `....///` 替换后还是 `..//`

**正确解法是双层防御**：

```java
private static final Pattern AVATAR_FILENAME =
        Pattern.compile("^[a-zA-Z0-9-]+\\.(jpg|jpeg|png)$");

// 第一层：白名单（语法级）
if (!AVATAR_FILENAME.matcher(filename).matches()) return 404;

// 第二层：路径包含（语义级）
Path baseDir = Path.of(uploadPath).toAbsolutePath().normalize();
Path resolved = baseDir.resolve(filename).normalize();
if (!resolved.startsWith(baseDir)) return 404;
```

第一层挡住 99% 的 payload；第二层是 backstop —— 将来谁把白名单放宽（比如加 `.gif`）忘了路径检查，第二层依然能阻止越界。

> **关键 API 细节**：
> - `Path.normalize()` 会 resolve `.` 和 `..` 段；不 normalize 直接 `startsWith` 没用。
> - `toAbsolutePath()` 必须在 `normalize()` 之前 —— 相对路径里的 `.` 不会被 resolve 到 cwd。
> - `Path.startsWith` 是按路径段比较（不是字符串前缀），不会把 `/upload-bad/foo` 误判为 `/upload/foo` 的前缀。
> - 两层都返回 404（不区分"格式错"和"文件不存在"），避免给攻击者枚举信息。
