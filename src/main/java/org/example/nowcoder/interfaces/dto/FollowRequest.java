package org.example.nowcoder.interfaces.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 关注目标。entityType 目前仅 ENTITY_TYPE_USER(3)。
 */
public record FollowRequest(
        @NotNull Integer entityType,
        @NotNull Integer entityId
) {}
