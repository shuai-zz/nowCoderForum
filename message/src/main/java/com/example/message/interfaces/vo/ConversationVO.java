package com.example.message.interfaces.vo;

import com.example.user.interfaces.vo.UserVO;

import java.util.Date;

/**
 * 私信会话项。
 *
 * @param target       对方用户
 * @param lastContent  会话中最近一条消息的文本
 * @param dmCount  会话内总消息数
 * @param unreadDmCount  当前用户在本会话内的未读数
 */
public record ConversationVO(
        String conversationId,
        UserVO target,
        String lastContent,
        Date lastCreateTime,
        int dmCount,
        int unreadDmCount
) {
    public static ConversationVO of(
            String conversationId,
            UserVO target,
            String lastContent,
            Date lastCreateTime,
            int dmCount,
            int unreadDmCount
    ) {
        return new ConversationVO(conversationId, target, lastContent, lastCreateTime, dmCount, unreadDmCount);
    }
}
