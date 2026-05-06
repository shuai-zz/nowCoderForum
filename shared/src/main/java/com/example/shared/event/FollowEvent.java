package com.example.shared.event;

public record FollowEvent(int userId, int entityType, int entityId, int entityUserId) {
}
