package org.example.nowcoder.service;

import com.github.pagehelper.PageInfo;
import org.example.nowcoder.entity.Message;
import org.example.nowcoder.entity.Page;

import java.util.List;

/**
 * @author zhaoshuai
 */
public interface MessageService {
    PageInfo<Message> findConversations(int userId, int pageNum, int pageSize);
    int findConversationCount(int userId);
    PageInfo<Message> findDms(String conversationId, int pageNum, int pageSize);
    int findDmCount(String conversationId);
    int findUnreadCount(int userId, String conversationId);
    int addMessage(Message message);
    int updateStatus(List<Integer> ids, int status);
    Message findLatestNotice(int userId, String topic);
    int findNoticeCount(int userId, String topic);
    int findNoticeUnreadCount(int userId, String topic);
    PageInfo<Message> findNotices(int userId, String topic, int pageNum, int pageSize);
}
