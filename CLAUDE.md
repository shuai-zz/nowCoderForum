# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot forum web app (nowCoder Forum) refactored into a multi-module REST API backend. Frontend is a separate Vue3 SPA repo (`nowcoder-forum-web`); infrastructure (MySQL/Redis/Kafka KRaft/ES + Nginx) lives in `nowcoder-forum-infra`. Supports user registration, posting, commenting, liking, following, private messaging, Elasticsearch-based search, and admin data analytics.

## Tech Stack

- **Framework**: Spring Boot 3.4.1, Java 21 (virtual threads enabled), Maven multi-module
- **Web**: Spring MVC, REST only (Thymeleaf removed in P6)
- **Database**: MySQL 8 + MyBatis Plus; schema migrations via **Flyway** (`system/src/main/resources/db/migration/`)
- **Search**: Elasticsearch (`DiscussPostRepository` extends `ElasticsearchRepository`)
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
1. `SecurityConfig` installs a custom `OncePerRequestFilter` before `UsernamePasswordAuthenticationFilter`. It reads the `ticket` cookie, validates the `LoginTicket` via `UserService`, and puts a `UsernamePasswordAuthenticationToken` into `SecurityContextHolder`.
2. `AuthTokenFilter` also reads the ticket cookie and stores the `User` in a `ThreadLocal` via `HostHolder` so controllers/services can access the current user.
3. `SecurityConfig.authorizeHttpRequests` enforces URL-level authorities:
   - `user`/`admin`/`moderator`: settings, upload, comment, post, letter, notice, like, follow
   - `moderator` only: `/discuss/top`, `/discuss/wonderful`
   - `admin` only: `/discuss/delete`, `/data/**`
4. `GlobalExceptionHandler` handles REST exceptions and returns JSON `Result`.

### Data Access Patterns
- **MyBatis Plus**: Mappers in each module's `infrastructure/mapper/`, XML in `*/src/main/resources/mapper/`. Pagination uses MP `Page`.
- **Elasticsearch**: `DiscussPostRepository` for CRUD; `DiscussPostRepositoryImpl` (Spring Data fragment) handles custom highlight search.
- **Redis**: Key conventions centralized in `RedisKeyUtil`.

### Caching Strategy & Read Models
- **Users**: `UserServiceImpl` caches users in Redis (`user:{id}`, TTL 1h); cache cleared on updates.
- **Login tickets**: Stored only in Redis (`ticket:{ticket}`); MySQL table dropped.
- **Likes (raw set)**: Who-liked-what is a Redis Set (`like:entity:{type}:{id}`); per-user total like count is a Redis counter (`like:user:{userId}`).
- **Likes (aggregate counts)**: Persisted on the entity:
  - `discuss_post.like_count` — maintained by `PostLikeEventListener`
  - `comment.like_count` — maintained by `CommentLikeEventListener`
  - `user_statistics.received_like_count` — maintained by `UserStatsEventListener`
- **Follows (raw graph)**: Sorted sets with timestamps as scores (`followee:{userId}:{entityType}`, `follower:{entityType}:{entityId}`) — used for paginated follower/followee lists ordered by follow time.
- **Follows (aggregate counts)**: `user_statistics.follower_count` / `followee_count` — maintained by `UserStatsEventListener`.
- **`likeStatus` (per-user, per-entity)**: NOT aggregated; queried at read time via `LikeService.findEntityLikeStatuses(userId, entityType, entityIds)` (Redis SISMEMBER pipelined). The view layer assembles aggregate count + per-user status before returning.

### Domain Events (Spring local) — Read-Model Maintenance
`interaction` is the write side. After every `like` / `unlike` / `follow` / `unfollow` it publishes a Spring `ApplicationEvent` (in-process, no Kafka):

- `EntityLikedEvent` / `EntityUnlikedEvent` (`shared/.../event/`)
- `FollowEvent` / `UnfollowEvent`

Read-side listeners update their own local tables:

| Listener | Module | Table | Trigger |
|---|---|---|---|
| `PostLikeEventListener`    | post | `discuss_post.like_count`         | EntityType=POST |
| `CommentLikeEventListener` | interaction | `comment.like_count`       | EntityType=COMMENT |
| `UserStatsEventListener`   | user | `user_statistics.{received_like_count, follower_count, followee_count}` | per event type |

`user_statistics` rows are inserted by `UserServiceImpl.register()` in the same transaction as the `user` insert — this guarantees subsequent `UPDATE … WHERE user_id = ?` always hits an existing row, so listeners can stay as pure increments (no UPSERT needed).

### Event-Driven Messaging (Kafka — cross-process notifications)
- `EventProducer` (in `shared`) publishes JSON events to Kafka topics for **cross-module side effects** (notifications, search index).
- **Message** module: `NotificationEventConsumer` listens `comment`/`like`/`follow` → creates system notification messages.
- **Search** module: `SearchIndexEventConsumer` listens `publish`/`delete` → indexes/removes posts in Elasticsearch.
- Topics are constants in `ForumConstant`.

> **何时用哪种？** Kafka 给"另一个 bounded context 的副作用"（通知、搜索索引）；Spring 本地事件给"同进程内的读模型"（点赞数、粉丝数）。后者无需 broker、零延迟、随事务一起回滚。

### Scheduled Jobs (Quartz)
- `PostScoreRefreshJob` (in `system` module) runs every 5 minutes. It pops post IDs from a Redis set (`post:score`), recalculates a Hacker-News-style score, updates the DB, and re-indexes the post in Elasticsearch.

### Configuration Notes
- **Root config**: `system/src/main/resources/application.yaml` holds the global infrastructure config (datasource, redis, kafka, es, mail, flyway). Each module also has a slim `application.yml` with `spring.application.name` + module-specific bits (mybatis-plus aliases, captcha, etc.).
- `application.yaml` imports `.env` via `optional:file:.env[.properties]`.
- Required env vars: `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `UPLOAD_PATH`. Optional: `CAPTCHA_PROVIDER` (default `kaptcha`), Tencent captcha keys.
- **No context path**: backend serves at `http://localhost:8080/api/v1/...` (the `/forum` context-path was dropped in P6).
- **Flyway**: enabled in `system/.../application.yaml`. Migrations live at `system/src/main/resources/db/migration/`. The `forum` database itself must be created externally before first boot (`CREATE DATABASE forum DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`); Flyway then handles all tables. `baseline-on-migrate: true` is set so an existing-DB-first-time-Flyway scenario works.
- **MyBatis Plus `type-aliases-package`** (root `application.yaml`): `com.example.user.domain,com.example.post.domain.entity,com.example.interaction.domain.entity,com.example.message.domain.entity`. Add new modules' entity packages here when introducing them.
- The `NowCoderApplication` `@PostConstruct` sets `es.set.netty.runtime.available.processors=false` to avoid a Netty/Elasticsearch startup conflict.

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
│   └── service/impl/
├── domain/              ← 领域实体（Entity）
│   └── entity/
└── infrastructure/      ← Mapper、Repository、Config、Security、MQ、Cache、Util
    ├── mapper/
    ├── config/
    ├── repository/
    └── util/
```

### 模块间依赖规则

1. **禁止跨模块访问 infrastructure**（如 user 模块直接调 post 模块的 Mapper）
2. 模块间通信通过 **application Service 接口** 或 **领域事件（Domain Event）**
3. 共享代码（`BizException`、`ForumConstant`、`Result`、`RedisKeyUtil`、`ValidationException`）收敛到 `shared` 包
4. 每个模块可独立演进，未来拆微服务时整体平移即可

---

## Migration History

> 旧 monolith → 多模块 + 前后端分离 + 领域事件解耦的改造已在多轮重构中完成。这里只保留最终对开发还有用的事实。

| 阶段 | 完成时间 | 关键产物 |
|---|---|---|
| P0–P5 | ~2026-04 之前 | 模块拆分 + REST API + Vue3 前端迁移 |
| P6 | 2026-04-21 | 删除 Thymeleaf / 老 Controller / `org.example.nowcoder.*` monolith 残留；Kafka KRaft；去掉 `/forum` context-path |
| P7 | 2026-05-06 | 业务循环解耦：`discuss_post.like_count` / `comment.like_count` / `user_statistics` 三个本地读模型 + 4 个 Spring 领域事件；`UserServiceImpl.register` 事务内同步建 stats 行；`FollowController` count 切到读模型；接入 Flyway（`V1__init_schema.sql`） |

### 当前已知技术债 / 后续 idea

- **Quartz 仍是 `memory` 模式**（dev 用）；生产部署需要切回 jdbc + 建 `qrtz_*` 表（详见 infra 仓库 `DEPLOY.md`）。
- **PageHelper 与 MyBatis-Plus 分页混用**：可统一到 MP，删 PageHelper 依赖。
- **`search → user` 的运行时调用**（搜索结果展示作者信息）：单体下没问题，将来拆微服务时改为 RPC/HTTP。
- **`system` 模块依赖较广**（Quartz job 调用了几乎所有业务 service）：拆微服务时这里要改异步事件。
- **新增模块时必须同步更新**根 `application.yaml` 的 `mybatis-plus.type-aliases-package`。

---

## DDD Architecture Audit (2026-05-07)

> 当前项目是"**DDD 包结构 + Transaction Script 实质**"：4 层目录摆得整齐，但每一层都做了不该做的事 — domain 在和框架谈恋爱，application 在继承 ORM 基类，interfaces 在直接操作基础设施。本节是一次完整审计，定位为简历项目的优化路线图。

### 🔴 P0 — 严格违反 DDD（面试一眼能看出的硬伤）

#### 1. Domain 实体被框架污染

**A. `User implements UserDetails`** — `user/src/main/java/com/example/user/domain/User.java:21`
- domain 实体直接 implements Spring Security 的 `UserDetails`，import 了 `org.springframework.security.*`
- 同时打 MyBatis Plus 的 `@TableName` / `@TableId`
- **改法**：保留 `User` 为纯 POJO；在 `user/infrastructure/security/` 下加 `UserDetailsAdapter implements UserDetails`，包一层

**B. `DiscussPost` 同时是 MySQL 实体 + ES 文档** — `post/src/main/java/com/example/post/domain/entity/DiscussPost.java`
- `@TableName("discuss_post")` + `@Document(indexName="discusspost")` + `@Setting(shards=6, replicas=3)` + 字段全打 `@Field`
- search 模块的 highlight 逻辑直接 set 到 `post.title`（`search/.../DiscussPostRepositoryImpl.java:66`），把 `<em>...</em>` 塞回 domain 字段
- **改法**：`post.domain.entity.DiscussPost`（纯 domain）+ `search.domain.SearchablePost`（ES 文档，独立类）

#### 2. Application Service 继承 MyBatis Plus 基类

```java
DiscussPostServiceImpl extends ServiceImpl<DiscussPostMapper, DiscussPost>
CommentServiceImpl     extends ServiceImpl<CommentMapper, Comment>
UserServiceImpl        extends ServiceImpl<UserMapper, User>
```
- 继承会把 `IService<T>` 的 100+ 方法（`saveBatch / removeById / listByIds / getOne`...）暴露成接口契约的一部分
- Controller 已经在用 `userService.listByIds(authIds)` 这种基类方法 → **接口契约被永久绑死在 MP**
- **改法**：去掉 `extends ServiceImpl`，需要的方法在 Service 接口里显式声明，impl 注入 `Mapper` 调用

#### 3. Controller 跨模块直接注入其他模块的 Mapper

`interaction/.../FollowController.java:47`：
```java
private final UserStatisticsMapper userStatisticsMapper;  // ← user 模块的 infrastructure
```
- 违反本文件"模块间依赖规则"第 1 条："禁止跨模块访问 infrastructure"
- **改法**：在 `UserService` 加 `getStatistics(int userId): UserStatistics` 方法

#### 4. Controller 直接操作 RedisTemplate

`PostController` / `LikeController` / `CommentController` 都注入了 `RedisTemplate<String, Object>` 直接做：
```java
redisTemplate.opsForSet().add(RedisKeyUtil.getPostScore(), post.getId());
```
- 接口层在做"把 postId 加入分数刷新队列"这种领域逻辑
- **改法**：封到 `DiscussPostService.markForScoreRefresh(int postId)`

---

### 🟠 P1 — 应该改，DDD 学习价值高

#### 5. Application DTO 跨模块持有他人的 domain 实体

```java
record PostItem(DiscussPost discussPost, User author, ...)         // post 持有 user.User
record FollowListItem(User user, Date followTime)                  // interaction 持有 user.User
record MessageItem(... User from, User target, ...)                // message 持有 user.User
```
- 跨 bounded context 不应该共享 domain 实体
- **改法**：传 ID 让 controller 组装；或者在每个模块定义 `AuthorRef(int id, String username, String avatar)` 这种局部 ValueObject（ACL/防腐层）
- 解掉这条后，post / message 在 application 层就不再需要注入 `UserService`

#### 6. Domain 模型贫血（Anemic Domain Model）

所有实体都是 `@Data` / 全 getter+setter，零业务行为。**应该收进 domain 的方法**：
```java
// User
boolean canDelete(DiscussPost p)  { return type == ADMIN; }
boolean canTopOrFeature()         { return type == MODERATOR || type == ADMIN; }
void activate(String code) { ... }       // 把 UserServiceImpl.activation 的规则收进来

// DiscussPost
void markAsTop()       { this.type = TOP; }
void markAsWonderful() { this.status = WONDERFUL; }
void softDelete()      { this.status = DELETED; }
boolean isDeleted()    { return status == DELETED; }
double calculateScore(long likeCount, Date epoch) { ... }   // 收 PostScoreRefreshJob 里的算分公式

// Comment
boolean isReply()  { return entityType == ENTITY_TYPE_COMMENT; }
boolean isOnPost() { return entityType == ENTITY_TYPE_POST; }
```
- 当前所有规则都堆在 `Service.updateXxx(id, value)` 这种 Transaction Script 里

#### 7. Application Service 含领域逻辑（应抽 Domain Service）

`DiscussPostServiceImpl.insertDiscussPost` / `CommentServiceImpl.addComment` / `MessageServiceImpl.addMessage` **三处重复**：
```java
discussPost.setTitle(HtmlUtils.htmlEscape(discussPost.getTitle()));
discussPost.setContent(HtmlUtils.htmlEscape(discussPost.getContent()));
discussPost.setTitle(sensitiveFilter.filter(discussPost.getTitle()));
discussPost.setContent(sensitiveFilter.filter(discussPost.getContent()));
```
- HTML 转义 + 敏感词过滤 = "用户产出内容"的领域规则
- **改法**：抽 `ContentSanitizer` 领域服务，三处复用

#### 8. search 模块没有自己的 Read Model

- `ElasticSearchServiceImpl` 直接 `import com.example.post.domain.entity.DiscussPost`
- ES 高亮 title/content 被回写到 `DiscussPost.title`，破坏 domain 完整性
- **改法**：
  - `search/domain/SearchablePost`（ES 文档结构）
  - `search/application/dto/SearchResult`（含独立的 highlight 字段，不污染 title）

#### 9. 模块包结构不一致

| 模块 | 问题 |
|---|---|
| `user/` | `domain/User.java` + `domain/LoginTicket.java` 在 domain 根；`domain/entity/UserStatistics.java` 在 entity/ |
| `system/` | 没有 `application/service/` 子包，`DataService.java` 直接放 `application/`，impl 放 `application/impl/` |
| `search/` | 完全没有 domain 层 |

#### 10. `UserStatsEventListener` 不区分 entityType

```java
@EventListener
public void onLiked(EntityLikedEvent event) {
    userStatisticsMapper.incrementReceivedLikeCount(event.entityUserId(), 1);  // 不分 type 就 +1
}
```
- 当前任何 entityType 的 like 都会增加用户的 received_like_count（包括评论的赞）
- 语义模糊，需要明确：是只统计帖子赞，还是所有内容的赞都算？
- **改法**：要么过滤 `event.entityType() == POST`，要么注释里明确"任何被赞内容都计数"

#### 11. `PostScoreRefreshJob` 用 `record` 实现 Job

```java
public record PostScoreRefreshJob(...) implements Job
```
- record 是不可变值对象，但 Job 是有副作用的服务执行单元
- 概念错位 — 应该是普通 `class`

---

### 🟡 P2 — 可接受（简历项目暂不修；面试时能解释即可）

#### 12. shared 模块过载（"shared kernel"边界爆炸）

```
shared/
├── aop/ServiceLogAspect.java          ← 切到所有 Service
├── captcha/CaptchaContext + Verifier  ← captcha 是具体业务
├── config/MybatisPlusConfig + RedisConfig
├── handler/GlobalExceptionHandler.java ← Web 层组件
├── messaging/Event + EventProducer    ← Kafka 集成
├── security/RestAccessDeniedHandler   ← Spring Security 适配
├── utils/SensitiveFilter              ← 敏感词是领域规则
└── ...
```
- 严格 DDD 的 shared kernel 只装"被多个 BC 共享的纯领域概念"（值对象、领域事件）
- 现在的 shared 实际上是"一切跨模块复用代码的垃圾桶"
- 真要修需要拆 4-5 个 starter 模块（`web-starter`、`messaging-starter`、`security-starter`、`captcha`），工作量大、收益小

#### 13. Kafka `Event` 类型不安全

`shared/messaging/Event.java`：
```java
private String topic;             // 字符串散落（用常量 mitigate）
private Map<String, Object> data; // 任意 K-V，没有 schema
```
- 跨进程 Kafka 用这种"散装事件"，靠 topic 字符串路由 + HashMap 携带 payload
- 比起本地 `EntityLikedEvent` 这种 record 弱很多
- 改成每个 topic 一个 record（`PublishPostEvent` / `LikeKafkaEvent` / `FollowKafkaEvent`）会更清晰

#### 14. 死代码 / `LoginTicket` 残留

- `UserServiceImpl` 里 3 处 `// loginTicketMapper.xxx(...)` 注释代码（`logout` / `getLoginTicket` / `updateAvatar`）
- `LoginTicket` 类在 `user/domain/`，但它已经是 Redis 缓存对象，不是领域实体 → 应挪到 `user/application/dto/` 或 `user/infrastructure/cache/`
- `User.activationCode` 字段：一次性用完后永久挂在主表里，可考虑挪走

#### 15. 事务边界不一致

| 方法 | `@Transactional` | 评价 |
|---|---|---|
| `UserServiceImpl.register` | ✅ | 正确（多表写入） |
| `UserServiceImpl.activation` / `updatePassword` | ❌ | 单条 update，可不要 |
| `DiscussPostServiceImpl.insertDiscussPost` | ❌ | 单写，可不要 |
| `CommentServiceImpl.addComment` | ✅ READ_COMMITTED | 正确（评论 + 帖子计数） |
| `MessageServiceImpl.addMessage` | ❌ | 单写，可不要 |
| `LikeServiceImpl.like` / `FollowServiceImpl.follow` | ❌ | Redis Lua + 发本地事件，需要单独考虑（事件失败不会回滚 Redis） |

- 现状能跑，缺一份"何时加 @Transactional"的规约

#### 16. `SecurityUtil.getCurrentUser()` 静态方法

`user/infrastructure/utils/SecurityUtil.java` 用静态 `SecurityContextHolder.getContext()...` 取当前用户。
- 单元测试需要 mock 静态调用
- DDD 风格倾向于注入 `CurrentUserProvider` 接口
- 但 Spring Security 标配，简历项目能接受

---

### 优化路线图

#### Phase R1 — 把硬伤先收拾掉（高 ROI，2-3 天）

| # | 任务 | 工作量 |
|---|---|---|
| R1.1 | `User` 拆成 POJO + `UserDetailsAdapter`（infra），切断 domain → Spring Security 依赖 | 半天 |
| R1.2 | `DiscussPost` 拆成 `post.DiscussPost`（MyBatis）+ `search.SearchablePost`（ES，独立 mapper），search 模块新建 domain 层 | 一天 |
| R1.3 | 三个 `XxxServiceImpl` 不再 `extends ServiceImpl<Mapper, Entity>`，需要的 Mapper 方法接口里显式声明 | 半天到一天 |
| R1.4 | `FollowController` 不再注 `UserStatisticsMapper`，加 `UserService.getStatistics(id)` | 小 |
| R1.5 | `PostController/LikeController/CommentController` 不直接操作 Redis，封到 `DiscussPostService.bumpScore(postId)` 之类 | 小 |

**R1 TODO（截至 2026-05-07）**

- [x] **R1.1** `User` 已拆为纯 POJO；`UserDetailsAdapter` 已建在 `user/infrastructure/security/`。  
- [x] **R1.2** `SearchablePost` 类已创建、`DiscussPost` 已去 ES 注解，但 **repository 层仍全程操作 `DiscussPost`**（`DiscussPostRepository` / `DiscussPostRepositoryImpl` / `ElasticSearchServiceImpl` 均未切到 `SearchablePost`），高亮仍回写 domain 字段。  
- [x] **R1.3** 三个 `ServiceImpl` 仍 `extends ServiceImpl`（`DiscussPostServiceImpl`、`CommentServiceImpl`、`UserServiceImpl`）。  
- [x] **R1.4** `FollowController` 仍直接注入 `UserStatisticsMapper`；`UserService` 尚无 `getStatistics(id)` 方法。（`UserController` 也直接注了 `UserStatisticsMapper`。）  
- [x] **R1.5** `PostController` / `LikeController` / `CommentController` 仍直接操作 `RedisTemplate` 刷 score 队列，未封装到 `DiscussPostService`。

**R2 TODO**

- [x] **R2.1** 给 `User` / `DiscussPost` / `Comment` 加领域行为方法：`User.activate(code)`、`DiscussPost.markAsTop()` / `markAsWonderful()` / `softDelete()` / `isDeleted()`、`Comment.isReply()` / `isOnPost()`；Service 层改为 `entity.activate(code); mapper.updateById(entity)` 模式
- [x] **R2.2** 抽取 `ContentSanitizer` 领域服务，统一 HTML 转义 + 敏感词过滤；`DiscussPostServiceImpl` / `CommentServiceImpl` / `MessageServiceImpl` 三处重复逻辑收编
- [x] **R2.3** 算分公式从 `PostScoreRefreshJob` 收进 `DiscussPost.calculateScore(long likeCount, long commentCount, Date createTime, Date epoch)` 静态方法
- [x] **R2.4** 跨模块 DTO 不再持有他人 domain 实体：定义 `AuthorRef(id, username, avatar)` ValueObject，替换 `PostItem` / `FollowListItem` / `MessageItem` 中的 `User` 字段

#### Phase R2 — Domain 模型充血化（DDD 加分项，1-2 天）

| # | 任务 |
|---|---|
| R2.1 | `User` / `DiscussPost` / `Comment` 加领域行为方法（`activate` / `markAsTop` / `softDelete` / `isDeleted` 等）；Service 改成 `entity.activate(code); userMapper.update(entity)` |
| R2.2 | 抽 `ContentSanitizer` 领域服务，HTML 转义 + 敏感词过滤三处复用统一 |
| R2.3 | 算分公式从 `PostScoreRefreshJob` 收进 `DiscussPost.calculateScore(...)` 静态方法 |
| R2.4 | 跨模块 DTO 不再持有他人 domain 实体：定义 `AuthorRef(id, username, avatar)` 这种局部 ValueObject |

#### Phase R3 — 模块结构与 Read Model（学习价值高，1-2 天）

| # | 任务 |
|---|---|
| R3.1 | search 模块建 `domain/SearchablePost` + `application/dto/SearchResult`，搜索高亮不再回写 post 实体 |
| R3.2 | user/domain 包结构统一：`User` / `LoginTicket` / `UserStatistics` 都进 `domain/entity/`（或都不进） |
| R3.3 | system 包结构统一：`application/service/{DataService, impl/...}` |
| R3.4 | `LoginTicket` 挪到 `user/application/dto/` 或 `user/infrastructure/cache/`，从 domain 删除 |
| R3.5 | `PostScoreRefreshJob` 从 record 改 class |
| R3.6 | `UserStatsEventListener.onLiked` 明确语义（要么过滤 entityType，要么注释说明） |

#### Phase R4 — 可选（简历项目暂时不做）

- shared 模块拆分（→ web-starter、messaging-starter、security-starter、captcha 模块）
- Kafka `Event` 类按 topic 拆成多个 record
- 事务注解策略统一（写一份 `@Transactional` 规约）
- 替换 `SecurityUtil` 静态方法为注入式 `CurrentUserProvider`
- PageHelper 完全迁出，统一用 MyBatis Plus 分页

