package org.example.nowcoder.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发布评论或回复。
 *
 * @param entityType  被评论的实体种类（ENTITY_TYPE_POST 或 ENTITY_TYPE_COMMENT）
 * @param entityId    被评论的实体 id
 * @param targetId    回复时被 @ 的用户 id，非回复场景传 0
 * @param postId      所属帖子 id（当 entityType=post 时等于 entityId；回复时传所在帖子的 id）
 * @param content     评论文本
 */
public record CreateCommentRequest(
        @NotNull(message = "entityType cannot be null") Integer entityType,
        @NotNull(message = "entityId cannot be null") Integer entityId,
        Integer targetId,
        Integer postId,
        @NotBlank(message = "Content cannot be empty") String content
) {}
