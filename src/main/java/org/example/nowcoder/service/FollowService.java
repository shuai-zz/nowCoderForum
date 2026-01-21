package org.example.nowcoder.service;

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
    List<Map<String,Object>> findFollowees(int userId, int pageNum, int pageSize);
    List<Map<String,Object>> findFollowers(int userId, int pageNum, int pageSize);

}
