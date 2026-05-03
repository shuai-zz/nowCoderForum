package com.example.interaction.application.service;

import java.util.List;
import java.util.Map;

/**
 * @author 23211
 */
public interface LikeService {
    void like(int userId, int entityType, int entityId,int entityUserId);
    long findEntityLikeCount(int entityTypePost, int entityId);
    int findEntityLikeStatus(int userId, int entityTypePost, int entityId);
    int findUserLikeCount(int userId);
    Map<Integer, Long> findEntityLikeCounts(int entityType, List<Integer> entityIds);
    Map<Integer, Integer> findEntityLikeStatuses(int userId, int entityType, List<Integer> entityIds);
}
