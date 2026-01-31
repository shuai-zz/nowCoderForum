package org.example.nowcoder.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Message;
import org.example.nowcoder.mapper.MessageMapper;
import org.example.nowcoder.service.MessageService;
import org.example.nowcoder.utils.SensitiveFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageMapper messageMapper;
    private final SensitiveFilter sensitiveFilter;

    @Override
    public PageInfo<Message> findConversations(int userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum,pageSize);
        List<Message> list = messageMapper.selectConversations(userId);
        return new PageInfo<>(list);
    }

    @Override
    public int findConversationCount(int userId) {
        return messageMapper.selectConversationCount(userId);
    }

    @Override
    public PageInfo<Message> findDms(String conversationId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum,pageSize);
        List<Message> list = messageMapper.selectDms(conversationId);
        return new PageInfo<>(list);

    }

    @Override
    public int findDmCount(String conversationId) {
        return messageMapper.selectDmCount(conversationId);
    }

    @Override
    public int findUnreadCount(int userId, String conversationId) {
        return messageMapper.selectUnreadCount(userId, conversationId);
    }

    @Override
    public int addMessage(Message message) {
        message.setContent(HtmlUtils.htmlEscape(message.getContent()));
        message.setContent(sensitiveFilter.filter(message.getContent()));
        return messageMapper.insertMessage(message);
    }

    @Override
    public int updateStatus(List<Integer> ids, int status) {
        return messageMapper.updateStatus(ids, status);
    }

    @Override
    public Message findLatestNotice(int userId, String topic) {
        return messageMapper.selectLatestNotice(userId, topic);
    }

    @Override
    public int findNoticeCount(int userId, String topic) {
        return messageMapper.selectNoticeCount(userId, topic);
    }

    @Override
    public int findNoticeUnreadCount(int userId, String topic) {
        return messageMapper.selectNoticeUnreadCount(userId, topic);
    }

    @Override
    public PageInfo<Message> findNotices(int userId, String topic, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum,pageSize);
        List<Message> list = messageMapper.selectNotices(userId, topic);
        return new PageInfo<>(list);
    }
}
