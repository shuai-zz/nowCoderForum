package org.example.nowcoder.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 点赞（toggle：已赞则取消）。
 */
public record LikeRequest(
        @NotNull Integer entityType,
        @NotNull Integer entityId,
        @NotNull Integer entityUserId,
        Integer postId
) {}
