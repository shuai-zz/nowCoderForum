package com.example.interaction.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.interaction.application.dto.FollowListItem;

import java.util.List;
import java.util.Map;

/**
 * @author zhaoshuai
 */
public interface FollowService {
    void follow(int userId, int entityType, int entityId);
    void unfollow(int userId, int entityType, int entityId);
    long findFolloweeCount(int userId, int entityType);
    long findFollowerCount(int entityType, int entityId);
    boolean hasFollowed(int userId, int entityType, int entityId);
    List<FollowListItem> findFollowees(int userId, int pageNum, int pageSize);
    List<FollowListItem> findFollowers(int userId, int pageNum, int pageSize);

}
