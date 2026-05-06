package com.example.shared.event;

public record UnfollowEvent(int userId, int entityType, int entityId, int entityUserId) {
}
