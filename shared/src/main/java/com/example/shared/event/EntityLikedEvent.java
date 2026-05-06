package com.example.shared.event;

public record EntityLikedEvent(int userId, int entityType, int entityId, int entityUserId) {
}
