package com.example.message.application.dto;

import com.example.user.domain.User;

import java.util.Date;

/**
 * 不需要时默认传0或null
 */
public record MessageItem(
        int id,
        User from,
        User to,
        String conversationId,
        String content,
        Date createTime,
        int status,
        int dmCount,
        int unreadDmCount
) {
    public static MessageItem of(
            int id,
            User from,
            User to,
            String conversationId,
            String content,
            Date createTime,
            int status,
            int dmCount,
            int unreadDmCount
    ) {
        return new MessageItem(
                id,
                from,
                to,
                conversationId,
                content,
                createTime,
                status,
                dmCount,
                unreadDmCount
        );
    }
}