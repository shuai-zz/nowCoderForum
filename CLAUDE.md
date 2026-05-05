# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot forum web application (nowCoder Forum) with server-side rendered Thymeleaf pages. It supports user registration, posting, commenting, liking, following, private messaging, Elasticsearch-based search, and admin data analytics.

## Tech Stack

- **Framework**: Spring Boot 4.0.1, Java 21, Maven
- **Web**: Spring MVC + Thymeleaf (server-side rendering)
- **Database**: MySQL 8 + MyBatis (XML mappers in `src/main/resources/mapper/`)
- **Search**: Elasticsearch (`DiscussPostRepository` extends `ElasticsearchRepository`)
- **Cache / Stats**: Redis (likes, follows, login tickets, user cache, UV/DAU with HyperLogLog and bitmaps)
- **Messaging**: Kafka for async events (comment, like, follow, publish, delete)
- **Scheduling**: Quartz (clustered JDBC job store) for post score refresh
- **Security**: Spring Security 6 + custom ticket-based authentication
- **Build**: `./mvnw` (Maven wrapper)

## Common Commands

```bash
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

### Request Flow & Authentication
1. `SecurityConfig` installs a custom `OncePerRequestFilter` before `UsernamePasswordAuthenticationFilter`. It reads the `ticket` cookie, validates the `LoginTicket` via `UserService`, and puts a `UsernamePasswordAuthenticationToken` into `SecurityContextHolder`.
2. `LoginTicketInterceptor` also reads the ticket cookie, validates it, and stores the `User` in a `ThreadLocal` via `HostHolder` so controllers/services can access the current user.
3. `SecurityConfig.authorizeHttpRequests` enforces URL-level authorities:
   - `user`/`admin`/`moderator`: settings, upload, comment, post, letter, notice, like, follow
   - `moderator` only: `/discuss/top`, `/discuss/wonderful`
   - `admin` only: `/discuss/delete`, `/data/**`
4. `ExceptionAdvice` handles global exceptions and distinguishes AJAX (`XMLHttpRequest`) from normal requests.

### Data Access Patterns
- **MyBatis**: Mappers (`DiscussPostMapper`, `CommentMapper`, `MessageMapper`, `UserMapper`, `LoginTicketMapper`) with XML in `src/main/resources/mapper/`. Pagination uses PageHelper (`PageHelper.startPage(pageNum, pageSize)`).
- **Elasticsearch**: `DiscussPostRepository` for CRUD; `ElasticSearchServiceImpl` uses `ElasticsearchOperations`/`ElasticsearchTemplate` for highlighted multi-match queries.
- **Redis**: Key conventions are centralized in `RedisKeyUtil`.

### Caching Strategy
- **Users**: `UserServiceImpl` caches users in Redis (key `user:{id}`, TTL 1h) and clears the cache on updates.
- **Login tickets**: Stored directly in Redis (`ticket:{ticket}`) instead of MySQL.
- **Likes**: Entity likes are Redis Sets (`like:entity:{type}:{id}`); user like counts are Redis Values (`like:user:{userId}`).
- **Follows**: Sorted sets with timestamps as scores (`followee:{userId}:{entityType}`, `follower:{entityType}:{entityId}`).

### Event-Driven Messaging (Kafka)
- `EventProducer` publishes JSON events to Kafka topics.
- `EventConsumer` listens:
  - `comment`, `like`, `follow` → creates system notification messages
  - `publish` → indexes the post in Elasticsearch
  - `delete` → removes the post from Elasticsearch
- Topics are defined as constants in `ForumConstant`.

### Scheduled Jobs (Quartz)
- `PostScoreRefreshJob` runs every 5 minutes. It pops post IDs from a Redis set (`post:score`), recalculates a Hacker-News-style score, updates the DB, and re-indexes the post in Elasticsearch.

### Configuration Notes
- `application.yaml` imports `.env` (`optional:file:.env[.properties]`).
- Required env variables in `.env`: `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `UPLOAD_PATH`.
- Context path is `/forum`, so local URLs are `http://localhost:8080/forum`.
- Thymeleaf cache is disabled (`spring.thymeleaf.cache: false`).
- The `NowCoderApplication` `@PostConstruct` sets `es.set.netty.runtime.available.processors=false` to avoid a Netty/Elasticsearch startup conflict.

## Current Package Layout (Migrated)

已完成从扁平包到 DDD 四层分层的迁移：

- `interfaces/` — REST Controller、DTO、VO、异常处理器、注解
- `application/` — Application Service（用例编排）
- `domain/` — 领域实体（Entity）
- `infrastructure/` — Mapper、Repository、Config、Security、MQ、Cache、Util、AOP、Interceptor
- `exception/` — 业务异常基类（顶层共享）

## Recommended Module-Based Architecture (Future)

当前按**技术层**分包（controller 放一起、service 放一起）。当业务继续增长时，建议进一步按**业务模块（限界上下文）**组织，每个模块内部再保留 DDD 四层：

```
org.example.nowcoder/
├── shared/                    ← 跨模块共享
│   ├── exception/
│   └── constant/
│
├── user/                      ← 用户模块（注册、登录、个人信息）
│   ├── interfaces/
│   │   └── rest/
│   │       └── AuthController.java
│   ├── application/
│   │   └── AuthService.java
│   ├── domain/
│   │   └── User.java
│   └── infrastructure/
│       └── UserMapper.java
│
├── post/                      ← 帖子模块
│   ├── interfaces/
│   │   └── rest/
│   │       └── PostController.java
│   ├── application/
│   │   └── PostService.java
│   ├── domain/
│   │   └── DiscussPost.java
│   └── infrastructure/
│       └── DiscussPostMapper.java
│       └── DiscussPostRepository.java
│
├── interaction/               ← 互动模块（评论、点赞、关注）
│   ├── interfaces/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── message/                   ← 消息模块（私信
、系统通知）
│   ├── interfaces/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── search/                    ← 搜索模块（Elasticsearch）
│   └── ...
│
└── system/                    ← 系统模块（数据统计、定时任务、验证码）
    └── ...
```

### 模块间依赖规则

1. **禁止跨模块访问 infrastructure**（如 user 模块直接调 post 模块的 Mapper）
2. 模块间通信通过 **application Service 接口** 或 **领域事件（Domain Event）**
3. 共享代码（如 `BizException`、`ForumConstant`）收敛到 `shared/` 包
4. 每个模块可独立演进，未来拆微服务时整体平移即可

### 何时迁移

当前项目规模下单体 + 按层分包已够用。当出现以下信号时考虑按模块拆分：

- 单个包内文件超过 30+（如 `application/service/` 下 Service 过多）
- 改一个功能需要同时打开 5+ 个不同层的包
- 有明确的拆服务计划

## Legacy Package Reference (Pre-Migration)

> 以下包名已废弃，仅用于阅读旧提交历史：

- `controller` — Thymeleaf page controllers and REST-like JSON endpoints
- `service` / `service.impl` — Business logic
- `mapper` — MyBatis mapper interfaces and Elasticsearch repository
- `entity` — Domain models (`User`, `DiscussPost`, `Comment`, `Message`, `LoginTicket`, `Event`, `Page`)
- `utils` — Utilities (`ForumUtil`, `CookieUtil`, `RedisKeyUtil`, `HostHolder`, `MailClient`, `SensitiveFilter`)
- `config` — Spring configuration beans (Security, Redis, Quartz, WebMvc, Kaptcha, ThreadPool)
- `event` — Kafka producer/consumer
- `quartz` — Quartz jobs
- `controller.interceptor` — MVC interceptors
- `controller.advice` — Global exception handling
