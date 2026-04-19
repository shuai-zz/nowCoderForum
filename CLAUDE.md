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

## Key Packages

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
