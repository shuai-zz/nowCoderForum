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
