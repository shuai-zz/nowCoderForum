package org.example.nowcoder.web.vo;

import java.util.Date;

/**
 * 私信会话项。
 *
 * @param target       对方用户
 * @param lastContent  会话中最近一条消息的文本
 * @param letterCount  会话内总消息数
 * @param unreadCount  当前用户在本会话内的未读数
 */
public record ConversationVO(
        String conversationId,
        UserVO target,
        String lastContent,
        Date lastCreateTime,
        int letterCount,
        int unreadCount
) {}
