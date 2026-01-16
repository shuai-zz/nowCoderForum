package org.example.nowcoder.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.entity.Message;
import org.example.nowcoder.mapper.MessageMapper;
import org.example.nowcoder.service.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author zhaoshuai
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
    private final MessageMapper messageMapper;

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
}
