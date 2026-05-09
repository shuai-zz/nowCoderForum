package com.example.user.interfaces.vo;


import com.example.shared.dto.AuthorRef;
import com.example.user.domain.entity.User;

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

    /**
     * 仅作展示，如帖子+username+userAvatar
     */
    public static UserVO from(AuthorRef auth){
        return new UserVO(
                auth.id(),
                auth.username(),
                // email
                null,
                // type
                0,
                // status
                0,
                auth.avatarUrl(),
                // createTime
                null
        );
    }
}