-- =============================================
-- V1__init_schema.sql
-- =============================================
-- 全量初始化脚本（新项目，不考虑老库迁移）。
--
-- 前置条件：
--   数据库 `forum` 必须已存在（由部署/DBA 一次性创建）。例如：
--     CREATE DATABASE forum DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   Flyway 不负责创建/选择数据库，它在 spring.datasource.url 指定的 schema 内运行。
--
-- 设计要点：
--   1. post: discuss_post.like_count / comment_count 是帖子固有展示属性，直接附在主表。
--   2. comment: comment.like_count 同 post，事件驱动维护，避免 Redis 丢失导致计数归零。
--   3. user: user_statistics 独立读模型表（不污染 user 主表），由领域事件消费后更新。
--   4. login_ticket 已迁 Redis，不建表。
--   5. 全部字段显式声明 NOT NULL + 默认值，避免 sql_mode 严格模式下空值插入失败。
--   6. user.username / user.email 提升为 UNIQUE，业务上本来就是唯一的。
--   7. user_id 统一 INT（旧 schema 把 discuss_post.user_id 写成 varchar(45) 是历史遗留）。
-- =============================================

-- ------------------------------
-- 1. 用户表
-- ------------------------------
CREATE TABLE `user` (
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `username`        VARCHAR(50)  NOT NULL,
    `password`        VARCHAR(50)  NOT NULL,
    `salt`            VARCHAR(50)  NOT NULL,
    `email`           VARCHAR(100) NOT NULL,
    `type`            INT          NOT NULL DEFAULT 0 COMMENT '0-普通用户; 1-超级管理员; 2-版主;',
    `status`          INT          NOT NULL DEFAULT 0 COMMENT '0-未激活; 1-已激活;',
    `activation_code` VARCHAR(100) NOT NULL DEFAULT '',
    `avatar_url`      VARCHAR(200) NOT NULL DEFAULT '',
    `create_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email`    (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------
-- 2. 用户统计读模型表
--    由领域事件（EntityLikedEvent / FollowEvent 等）消费后更新
--    注册时由 UserService 同步 INSERT 一行（保证 increment 必落到已有行）
-- ------------------------------
CREATE TABLE `user_statistics` (
    `user_id`             INT       NOT NULL,
    `received_like_count` INT       NOT NULL DEFAULT 0 COMMENT '收到的总赞数',
    `follower_count`      INT       NOT NULL DEFAULT 0 COMMENT '粉丝数',
    `followee_count`      INT       NOT NULL DEFAULT 0 COMMENT '关注数',
    `updated_time`        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_user_statistics_user`
        FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户统计读模型（领域事件消费后更新）';

-- ------------------------------
-- 3. 帖子表
--    like_count / comment_count 都是固有展示属性，事件驱动维护
-- ------------------------------
CREATE TABLE `discuss_post` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `user_id`       INT          NOT NULL,
    `title`         VARCHAR(100) NOT NULL,
    `content`       TEXT         NOT NULL,
    `type`          INT          NOT NULL DEFAULT 0 COMMENT '0-普通; 1-置顶;',
    `status`        INT          NOT NULL DEFAULT 0 COMMENT '0-正常; 1-精华; 2-拉黑;',
    `comment_count` INT          NOT NULL DEFAULT 0 COMMENT '评论数',
    `like_count`    INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    `score`         DOUBLE       NOT NULL DEFAULT 0 COMMENT 'Hacker-News 风格热度',
    `create_time`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_discuss_post_user_id`     (`user_id`),
    KEY `idx_discuss_post_create_time` (`create_time`),
    KEY `idx_discuss_post_score`       (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子表';

-- ------------------------------
-- 4. 评论表
--    评论可被点赞，like_count 持久化，与 discuss_post 对称
--    entity_type/entity_id：被评论对象（帖子或上级评论）
--    target_id：被评论对象的作者，给评论作者用，用于 @ / 通知
-- ------------------------------
CREATE TABLE `comment` (
    `id`          INT       NOT NULL AUTO_INCREMENT,
    `user_id`     INT       NOT NULL,
    `entity_type` INT       NOT NULL COMMENT '1-帖子; 2-评论;',
    `entity_id`   INT       NOT NULL,
    `target_id`   INT       NOT NULL DEFAULT 0 COMMENT '被评论方作者 id, 0 表示无',
    `content`     TEXT      NOT NULL,
    `like_count`  INT       NOT NULL DEFAULT 0 COMMENT '点赞数',
    `status`      INT       NOT NULL DEFAULT 0 COMMENT '0-正常; 1-删除;',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_comment_user_id`         (`user_id`),
    KEY `idx_comment_entity_type_id`  (`entity_type`, `entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ------------------------------
-- 5. 消息表（私信 + 系统通知）
--    conversation_id：私信会话 id（如 "1_2"，小 id 在前）；系统通知则为通知 topic
-- ------------------------------
CREATE TABLE `message` (
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `from_id`         INT          NOT NULL COMMENT '发送方 id, 1 表示系统',
    `to_id`           INT          NOT NULL COMMENT '接收方 id',
    `conversation_id` VARCHAR(45)  NOT NULL,
    `content`         TEXT         NOT NULL,
    `status`          INT          NOT NULL DEFAULT 0 COMMENT '0-未读; 1-已读; 2-删除;',
    `create_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_message_from_id`         (`from_id`),
    KEY `idx_message_to_id`           (`to_id`),
    KEY `idx_message_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';