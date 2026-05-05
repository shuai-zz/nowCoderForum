package com.example.message.application.service;


import com.example.message.application.dto.MessageItem;
import com.example.message.domain.entity.Message;
import com.example.shared.result.PageData;

import java.util.List;
import java.util.Map;

/**
 * @author zhaoshuai
 */
public interface MessageService {
    PageData<MessageItem> findConversations(int userId, int pageNum, int pageSize);
    int findConversationCount(int userId);
    PageData<MessageItem> findDms(String conversationId, int pageNum, int pageSize);
    int findDmCount(String conversationId);
    Map<String, Integer> findDmCounts(List<String> conversationIds);
    int findUnreadCount(int userId, String conversationId);
    int addMessage(Message message);
    int updateStatus(List<Integer> ids, int status);
    Message findLatestNotice(int userId, String topic);
    int findNoticeCount(int userId, String topic);
    int findNoticeUnreadCount(int userId, String topic);
    PageData<MessageItem> findNotices(int userId, String topic, int pageNum, int pageSize);

    Map<String, Integer> findUnreadDmCounts(int id, List<String> conversationIds);

}
