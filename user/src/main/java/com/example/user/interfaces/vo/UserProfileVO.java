package com.example.user.interfaces.vo;

/**
 * 用户主页视图：基础信息 + 获赞总数 + 关注/粉丝数 + 当前用户是否已关注该用户。
 */
public record UserProfileVO(
        UserVO user,
        long likeCount,
        long followeeCount,
        long followerCount
) {}
