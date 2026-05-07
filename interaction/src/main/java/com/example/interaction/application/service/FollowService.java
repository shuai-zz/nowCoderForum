package com.example.interaction.application.service;

import com.example.interaction.application.dto.FollowListItem;

import java.util.List;

/**
 * @author zhaoshuai
 */
public interface FollowService {
    void follow(int userId, int entityType, int entityId, int entityUserId);
    void unfollow(int userId, int entityType, int entityId, int entityUserId);
    boolean hasFollowed(int userId, int entityType, int entityId);
    List<FollowListItem> findFollowees(int userId, int pageNum, int pageSize);
    List<FollowListItem> findFollowers(int userId, int pageNum, int pageSize);

}
