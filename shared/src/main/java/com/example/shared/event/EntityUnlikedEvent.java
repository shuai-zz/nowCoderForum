package com.example.shared.event;

public record EntityUnlikedEvent(int userId, int entityType, int entityId, int entityUserId) {
}
