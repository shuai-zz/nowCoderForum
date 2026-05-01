package com.example.interaction.interfaces.vo;

import com.example.user.interfaces.vo.UserVO;

import java.util.Date;

/**
 * 关注/粉丝列表中的用户项。
 * @param hasFollowed 当前登录用户是否已关注该 user；未登录固定 false
 */
public record FollowUserVO(UserVO user, Date followTime, boolean hasFollowed) {}
