# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot forum web application (nowCoder Forum) with server-side rendered Thymeleaf pages. It supports user registration, posting, commenting, liking, following, private messaging, Elasticsearch-based search, and admin data analytics.

## Tech Stack

- **Framework**: Spring Boot 3.4.1, Java 21, Maven
- **Wewob**: Spring MVC + Thymeleaf (server-side rendering)
- **Database**: MySQL 8 + MyBatis Plus (XML mappers in `*/src/main/resources/mapper/`)
- **Search**: Elasticsearch (`DiscussPostRepository` extends `ElasticsearchRepository`)
- **Cache / Stats**: Redis (likes, follows, login tickets, user cache, UV/DAU with HyperLogLog and bitmaps)
- **Messaging**: Kafka for async events (comment, like, follow, publish, delete)
- **Scheduling**: Quartz (`job-store-type: memory`) for post score refresh
- **Security**: Spring Security 6 + custom ticket-based authentication
- **Build**: `./mvnw` (Maven wrapper), multi-module aggregate build

## Common Commands

```bash
# Install all modules to local repo (required before root can resolve them)
./mvnw clean install -DskipTests

# Run the application (port 8080, context-path /forum)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=NowCoderApplicationTests

# Run a single test method
./mvnw test -Dtest=NowCoderApplicationTests#contextLoads

# Build
./mvnw clean package
```

## Architecture

### Multi-Module Structure

根 POM (`com.example:nowcoder-parent`) 是聚合父 POM，继承 `spring-boot-starter-parent:3.4.1`，管理 7 个子模块：

```
nowcoder-parent (pom)
├── shared/          ← 跨模块共享（exception、constant、result、util）
├── user/            ← 用户模块（注册、登录、个人信息）
├── post/            ← 帖子模块
├── interaction/     ← 互动模块（评论、点赞、关注）
├── message/         ← 消息模块（私信、系统通知）
├── search/          ← 搜索模块（Elasticsearch）
├── system/          ← 系统模块（数据统计、定时任务、验证码）
└── src/main/        ← 根应用入口（NowCoderApplication）+ 旧 monolith 残留
```

模块间依赖（当前状态）：
- `shared` ← 无内部依赖（已移除对根的循环依赖）
- `user` → `shared`, `interaction`
- `post` → `interaction`
- `interaction` → `shared`, `user`, `post`
- `message` → `shared`
- `search` → `user`
- `system` → `shared`, `post`, `interaction`, `search`

**注意**：`user ↔ interaction ↔ post` 之间有循环调用关系（业务耦合），pom 层面已没有直接循环，但运行时逻辑耦合仍需后续梳理。

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

### Caching Strategy
- **Users**: `UserServiceImpl` caches users in Redis (key `user:{id}`, TTL 1h) and clears the cache on updates.
- **Login tickets**: Stored directly in Redis (`ticket:{ticket}`) instead of MySQL.
- **Likes**: Entity likes are Redis Sets (`like:entity:{type}:{id}`); user like counts are Redis Values (`like:user:{userId}`).
- **Follows**: Sorted sets with timestamps as scores (`followee:{userId}:{entityType}`, `follower:{entityType}:{entityId}`).

### Event-Driven Messaging (Kafka)
- `EventProducer` (in `shared`) publishes JSON events to Kafka topics.
- **Message** module: `NotificationEventConsumer` listens `comment`/`like`/`follow` → creates system notification messages.
- **Search** module: `SearchIndexEventConsumer` listens `publish`/`delete` → indexes/removes posts in Elasticsearch.
- **Old monolith**: `EventConsumer` (未拆分) still handles all topics — needs to be split and removed.
- Topics are defined as constants in `ForumConstant`.

### Scheduled Jobs (Quartz)
- `PostScoreRefreshJob` (in `system` module) runs every 5 minutes. It pops post IDs from a Redis set (`post:score`), recalculates a Hacker-News-style score, updates the DB, and re-indexes the post in Elasticsearch.

### Configuration Notes
- 根 `application.yaml` 包含完整的全局基础设施配置（datasource、redis、kafka、es、mail 等）。
- 各模块 `application.yml` 只包含 `spring.application.name` + 模块特有配置（如 mybatis-plus 别名包、quartz、captcha 等）。
- `application.yaml` imports `.env` (`optional:file:.env[.properties]`).
- Required env variables in `.env`: `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `UPLOAD_PATH`.
- Context path is `/forum`, so local URLs are `http://localhost:8080/forum`.
- Thymeleaf cache is disabled (`spring.thymeleaf.cache: false`).
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

## Migration TODO

以下清单记录从旧 monolith 向多模块迁移的剩余工作。

### A. `src/main/java/org/example/nowcoder/` 残留文件

| 文件 | 状态 | 下一步 |
|---|---|---|
| `NowCoderApplication.java` | 保留 | 主入口，等拆多应用时再处理 |
| `domain/entity/Page.java` | **未使用** | 零外部引用，可删除（所有模块用 MP 的 `Page`） |
| `infrastructure/aop/ServiceLogAspect.java` | 切入点失效 | pointcut 还是 `org.example.nowcoder.application.service.*`，需改为 `com.example.*.application.service.*.*(..)` 或删除 |
| `infrastructure/config/CorsConfig.java` | 保留 | 全局 Web 配置，不属于单一模块 |
| `infrastructure/config/ThreadPoolConfig.java` | 保留 | `@EnableScheduling` + `@EnableAsync`，全局配置 |
| `infrastructure/mapper/LoginTicketMapper.java` | `@Deprecated` | 登录票已迁 Redis，等最终清理时删除 |
| `infrastructure/messaging/EventConsumer.java` | **需拆分** | 跨模块消费者（message + search + post），应拆成各模块自己的 Consumer 后删除 |
| `infrastructure/util/CookieUtil.java` | **未使用** | 零外部引用，疑似已废弃，可删除 |
| `infrastructure/util/HostHolder.java` | `@Deprecated` | 引用了旧 `User`。若搬进 shared 会造成 `shared → user` 循环依赖。应等安全架构重构（用 `Authentication` 替代 `HostHolder`） |
| `infrastructure/util/ThymeleafDateFormatter.java` | `@Deprecated` | 零外部引用，可删除 |
| `interfaces/annotation/LoginRequired.java` | **未使用** | 被 Spring Security 的 `@PreAuthorize` 替代，可删除 |
| `interfaces/handler/GlobalExceptionHandler.java` | 需决策 | `@RestControllerAdvice`，跨模块全局异常处理。搬去 shared 需要给 shared 加 web 依赖，目前放在根或等专门的 web-starter 模块 |

### B. 跨模块旧包引用（`org.example.nowcoder.*`）

以下文件仍 import 旧 monolith 路径，需要逐一修复：

**shared 模块（2 个文件）**：
- `shared/security/AuthTokenFilter.java` → `import org.example.nowcoder.infrastructure.util.HostHolder;`
- `shared/interceptor/DataInterceptor.java` → `import org.example.nowcoder.domain.entity.User;`、`...application.service.DataService;`、`...infrastructure.util.HostHolder;`

**user 模块（1 个文件）**：
- `user/interfaces/rest/AuthController.java` → `import org.example.nowcoder.infrastructure.captcha.CaptchaContext;`、`...captcha.CaptchaVerifier;`
  - **注意**：captcha 类已搬到 `system` 模块，AuthController 引用旧路径说明 import 未更新

**system 模块（6 个文件）**：
- `system/application/impl/DataServiceImpl.java` → `DataService`、`RedisKeyUtil`
- `system/infrastructure/captcha/KaptchaVerifier.java` → `ValidationException`、`RedisKeyUtil`
- `system/infrastructure/captcha/TencentCaptchaVerifier.java` → `ValidationException`
- `system/infrastructure/config/QuartzConfig.java` → `PostScoreRefreshJob`（system 模块内同名类已存在，import 应改为同模块）
- `system/infrastructure/quartz/PostScoreRefreshJob.java` → `DiscussPost`、`DiscussPostService`、`ElasticSearchService`、`LikeService`、`RedisKeyUtil`
- `system/interfaces/rest/DataController.java` → `Result`

**旧 monolith（5 个文件，预期存在）**：
- `NowCoderApplication.java`、`LoginTicketMapper.java`、`EventConsumer.java`、`HostHolder.java`、`GlobalExceptionHandler.java`

**测试文件（3 个文件）**：
- `src/test/java/org/example/nowcoder/MailTest.java`
- `src/test/java/org/example/nowcoder/RedisTests.java`
- `src/test/java/org/example/nowcoder/SensitiveWordTest.java`

### C. 模块依赖问题

1. **`user → interaction` 和 `post → interaction`（业务循环）**：
   user 和 post 模块都依赖 interaction 模块（取 likeCount、follow 状态）。而 interaction 又依赖 user 和 post（需要 UserService、DiscussPostService）。这是**业务逻辑层面的循环**，pom 层面已没有直接循环，需要通过**领域事件 + 本地读模型**解耦（详见下方“业务循环解耦方案”）。

2. **`search → user`**：
   search 模块依赖 user 模块（`UserService`）。这比较合理（搜索结果的作者信息）。如果将来 search 独立部署，可以改为通过 RPC/HTTP 查询用户服务。

3. **`system → post/interaction/search`**：
   system 模块依赖较广（PostScoreRefreshJob 需要 DiscussPostService、LikeService、ElasticSearchService）。这符合 system 作为“系统/运维”模块的定位，但如果将来拆微服务，这些调用应改为异步事件或内部 API。

### D. 业务循环解耦方案（领域事件 + 本地读模型）

**核心原则**：谁产生行为，谁发事件；谁展示数据，谁维护读模型。

不通过共享 Redis key（避免隐式契约风险），也不把统计量混入主表固有属性。interaction 模块行为发生后发布**领域事件**，post / user 模块各自监听并维护自己的**本地读模型**。

#### 1. 事件定义（放在 `shared` 模块）

```java
// shared/messaging/event/EntityLikedEvent.java
public record EntityLikedEvent(int userId, int entityType, int entityId, int entityUserId) {}

// shared/messaging/event/EntityUnlikedEvent.java
public record EntityUnlikedEvent(int userId, int entityType, int entityId, int entityUserId) {}

// shared/messaging/event/FollowEvent.java
public record FollowEvent(int userId, int entityType, int entityId, int entityUserId) {}

// shared/messaging/event/UnfollowEvent.java
public record UnfollowEvent(int userId, int entityType, int entityId, int entityUserId) {}
```

#### 2. 本地读模型设计

**post 模块**：在 `discuss_post` 表增加 `like_count INT DEFAULT 0`（帖子热度是帖子固有展示属性，同 `comment_count`）。

**user 模块**：新建 **独立读模型表**（不污染用户固有属性）：

```sql
CREATE TABLE user_statistics (
    user_id INT PRIMARY KEY,
    received_like_count INT DEFAULT 0 COMMENT '收到的总赞数',
    follower_count INT DEFAULT 0 COMMENT '粉丝数',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

> 独立表的好处：未来可换存储（ES/ClickHouse），不影响 `user` 主表结构。

#### 3. 写入流程（interaction 模块）

`LikeService.like()` / `FollowService.follow()` 等行为完成后，发布 Spring 本地事件：

```java
@Service
public class LikeServiceImpl implements LikeService {
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public void like(int userId, int entityType, int entityId, int entityUserId) {
        // 1. 写 Redis（现有行为：记录谁点了赞）
        redisTemplate.opsForSet().add(RedisKeyUtil.getEntityLikeKey(entityType, entityId), userId);
        
        // 2. 发布领域事件（不 care 谁消费、怎么消费）
        eventPublisher.publishEvent(new EntityLikedEvent(userId, entityType, entityId, entityUserId));
    }
}
```

#### 4. 监听更新（post / user 模块）

**post 模块**：

```java
@Component
@RequiredArgsConstructor
public class PostLikeEventListener {
    private final DiscussPostMapper discussPostMapper;
    
    @EventListener
    public void onLiked(EntityLikedEvent event) {
        if (event.entityType() == ENTITY_TYPE_POST) {
            discussPostMapper.incrementLikeCount(event.entityId(), 1);
        }
    }
    
    @EventListener
    public void onUnliked(EntityUnlikedEvent event) {
        if (event.entityType() == ENTITY_TYPE_POST) {
            discussPostMapper.incrementLikeCount(event.entityId(), -1);
        }
    }
}
```

**user 模块**：

```java
@Component
@RequiredArgsConstructor
public class UserStatsEventListener {
    private final UserStatisticsMapper userStatisticsMapper;
    
    @EventListener
    public void onLiked(EntityLikedEvent event) {
        userStatisticsMapper.incrementReceivedLikeCount(event.entityUserId(), 1);
    }
    
    @EventListener
    public void onFollowed(FollowEvent event) {
        userStatisticsMapper.incrementFollowerCount(event.entityUserId(), 1);
    }
}
```

#### 5. 查询改造（解除依赖）

**post 模块**组装 `PostItem` 时不再调 `LikeService`：

```java
// 改造前：调 interaction 模块
Map<Integer, Long> likeCountMap = likeService.findEntityLikeCounts(ENTITY_TYPE_POST, postIds);

// 改造后：读自己的字段
return new PostItem(post, user, (long) post.getLikeCount(), 0);
```

**user 模块**查个人页时不再调 `FollowService`：

```java
// 改造前：调 interaction 模块
long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);

// 改造后：读自己的统计表
UserStatistics stats = userStatisticsMapper.selectById(userId);
```

#### 6. 关于 `likeStatus`（当前用户是否点赞）

`likeStatus` 是**交互状态**（不是聚合统计量），列表查询时仍然需要。解耦后的推荐做法：

- **Service 层**：只返回不含 `likeStatus` 的纯数据（`PostItem` 去掉 `likeStatus`）
- **Controller 层**：二次组装，batch 调用 `LikeService.findEntityLikeStatuses()` 查状态
- 这样 `post` 模块的 **Service 层**不再依赖 `interaction`，只有 **Controller（编排层）** 依赖，分层更干净

#### 7. 实施优先级

| 优先级 | 任务 | 说明 |
|---|---|---|
| **P0** | `discuss_post` 表加 `like_count` 字段 | 解除 `post → interaction` 的最核心查询 |
| **P0** | 新建 `user_statistics` 表 | 解除 `user → interaction` 的 follower/receivedLike 查询 |
| **P1** | `shared` 模块定义 4 个领域事件 | `EntityLikedEvent`、`EntityUnlikedEvent`、`FollowEvent`、`UnfollowEvent` |
| **P1** | `interaction` 模块在 like/follow 方法里发布事件 | 用 `ApplicationEventPublisher`，单体下不需要 Kafka |
| **P1** | `post` / `user` 模块写 EventListener | 更新自己的读模型 |
| **P1** | 改造 `DiscussPostServiceImpl` / `UserServiceImpl` 查询 | 从本地读模型取数，删除对 interaction Service 的调用 |
| **P2** | Controller 层处理 `likeStatus` | 列表查询时 Controller batch 查状态并组装 VO |

#### 8. 效果

| 改造前 | 改造后 |
|---|---|
| `post → interaction`（查 likeCount） | `post` 读自己的 `like_count`，**解除依赖** |
| `user → interaction`（查 followerCount） | `user` 读自己的 `user_statistics`，**解除依赖** |
| `interaction → post`（comment 需要帖子信息） | 保留（评论天然依附帖子） |
| `interaction → user`（follow 需要用户信息） | 保留（关注天然依附用户） |

**业务循环从双向变成了单向**，代码结构更清晰，为将来拆微服务时直接复用事件结构。

### E. 已知不一致

- **MyBatis Plus `type-aliases-package`**：根 `application.yaml` 中配置为 `com.example.user.domain,com.example.post.domain.entity,com.example.interaction.domain.entity,com.example.message.domain.entity`。新增模块实体时需要同步追加。
- **`springdoc.version`**：根 pom 统一为 `3.0.2`，与模块实际一致。

---

## Legacy Package Reference (Pre-Migration)

> 以下包名已废弃，代码中仍可能残留 import。若看到 `org.example.nowcoder.*` 的 import，优先改为对应的新模块路径：

| 旧包 | 新位置 |
|---|---|
| `org.example.nowcoder.domain.entity` | 分散到各模块 `domain/entity/` |
| `org.example.nowcoder.application.service` | 分散到各模块 `application/service/` |
| `org.example.nowcoder.infrastructure.mapper` | 分散到各模块 `infrastructure/mapper/` |
| `org.example.nowcoder.infrastructure.util` | `com.example.shared.utils` |
| `org.example.nowcoder.exception` | `com.example.shared.exception` |
| `org.example.nowcoder.interfaces.common` | `com.example.shared.result`（Result）等 |
| `org.example.nowcoder.infrastructure.config` | 按职责分散到各模块 `infrastructure/config/` 或根保留 |
| `org.example.nowcoder.event` | `com.example.shared.messaging` |
| `org.example.nowcoder.quartz` | `com.example.system.infrastructure.quartz` |
| `org.example.nowcoder.controller.interceptor` | `com.example.shared.interceptor` |
| `org.example.nowcoder.controller.advice` | 根保留或 `com.example.shared.advice` |
