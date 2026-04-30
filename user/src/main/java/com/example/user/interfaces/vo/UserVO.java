package com.example.user.interfaces.vo;


import com.example.user.domain.User;

import java.util.Date;

/**
 * 对外暴露的用户视图。永远不包含 password / salt / activationCode。
 */
public record UserVO(
        int id,
        String username,
        String email,
        int type,
        int status,
        String avatarUrl,
        Date createTime
) {
    public static UserVO from(User u) {
        if (u == null) {
            return null;
        }
        return new UserVO(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getType(),
                u.getStatus(),
                u.getAvatarUrl(),
                u.getCreateTime()
        );
    }
}