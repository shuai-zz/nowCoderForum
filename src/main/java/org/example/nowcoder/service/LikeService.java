package org.example.nowcoder.service;

/**
 * @author 23211
 */
public interface LikeService {
    void like(int userId, int entityType, int entityId);
    long findEntityLikeCount(int entityTypePost, int entityId);
    int findEntityLikeStatus(int userId, int entityTypePost, int entityId);
}
